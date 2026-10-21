package com.labguard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labguard.model.AuditEvent;
import com.labguard.model.Hazard;
import com.labguard.model.HazardStatus;
import com.labguard.model.WorkOrder;
import com.labguard.model.WorkOrderDetail;
import com.labguard.model.WorkOrderStatus;
import com.labguard.repository.AuditEventRepository;
import com.labguard.repository.HazardRepository;
import com.labguard.repository.WorkOrderRepository;
import com.labguard.workflow.WorkOrderStateMachine;
import com.labguard.workflow.WorkflowAction;
import com.labguard.workflow.WorkflowException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 整改工单业务。所有状态流转都经过 {@link WorkOrderStateMachine} 校验，
 * 再由条件更新保证并发安全，状态变更与审计写入在同一事务内完成。
 */
@Service
public class WorkOrderService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WorkOrderRepository workOrderRepository;
    private final HazardRepository hazardRepository;
    private final AuditEventRepository auditEventRepository;

    public WorkOrderService(WorkOrderRepository workOrderRepository,
                            HazardRepository hazardRepository,
                            AuditEventRepository auditEventRepository) {
        this.workOrderRepository = workOrderRepository;
        this.hazardRepository = hazardRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * 由已确认的隐患创建工单。未确认、重复建单、责任人缺失、截止日期非法都会被拒绝。
     */
    @Transactional
    public long create(long hazardId, String assignee, LocalDate dueDate, String note, String operator) {
        if (assignee == null || assignee.isBlank()) {
            throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT, "请填写责任人");
        }
        if (dueDate == null) {
            throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT, "请选择截止日期");
        }
        if (dueDate.isBefore(LocalDate.now())) {
            throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT, "截止日期不能早于今天");
        }

        Hazard hazard = hazardRepository.findById(hazardId)
                .orElseThrow(() -> new WorkflowException(
                        WorkflowException.Code.HAZARD_NOT_FOUND, "隐患不存在或已被删除"));
        if (hazard.status() != HazardStatus.CONFIRMED) {
            throw new WorkflowException(WorkflowException.Code.HAZARD_NOT_CONFIRMED,
                    "风险未确认，不能创建工单");
        }
        if (workOrderRepository.existsByHazardId(hazardId)) {
            throw new WorkflowException(WorkflowException.Code.HAZARD_ALREADY_HAS_ORDER,
                    "该隐患已存在整改工单，不能重复创建");
        }

        WorkOrder order = WorkOrder.create(hazardId, assignee.trim(), dueDate,
                note == null || note.isBlank() ? null : note.trim());
        long id = workOrderRepository.insert(order);

        auditEventRepository.insert(AuditEvent.of(
                AuditEvent.ENTITY_WORK_ORDER,
                id,
                WorkflowAction.CREATE_WORK_ORDER.name(),
                null,
                WorkOrderStatus.PENDING_REMEDIATION.name(),
                operator,
                "创建整改工单",
                null));
        return id;
    }

    /** 责任人提交整改说明与整改照片，工单进入待复查。 */
    @Transactional
    public void submitRemediation(long workOrderId, String remediationNote,
                                  String afterImagePath, String operator) {
        if (remediationNote == null || remediationNote.isBlank()) {
            throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT, "请填写整改说明");
        }
        if (afterImagePath == null || afterImagePath.isBlank()) {
            throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT, "请上传整改后照片");
        }

        WorkOrder order = requireOrder(workOrderId);
        WorkOrderStateMachine.assertTransition(order.status(), WorkOrderStatus.PENDING_REVIEW);

        int updated = workOrderRepository.submitRemediation(
                workOrderId, remediationNote.trim(), afterImagePath.trim());
        if (updated == 0) {
            throw illegalTransitionAfterRecheck(workOrderId);
        }

        String detail = toJson(Map.of(
                "remediationNote", remediationNote.trim(),
                "afterImagePath", afterImagePath.trim()));
        auditEventRepository.insert(AuditEvent.of(
                AuditEvent.ENTITY_WORK_ORDER,
                workOrderId,
                WorkflowAction.SUBMIT_REMEDIATION.name(),
                WorkOrderStatus.PENDING_REMEDIATION.name(),
                WorkOrderStatus.PENDING_REVIEW.name(),
                operator,
                "提交整改",
                detail));
    }

    /**
     * 复查。通过则关闭工单，退回则回到待整改并保留复查意见。
     * 退回时复查意见必填。
     */
    @Transactional
    public void review(long workOrderId, boolean passed, String reviewComment, String operator) {
        if (!passed && (reviewComment == null || reviewComment.isBlank())) {
            throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT,
                    "退回整改时必须填写复查意见");
        }

        WorkOrder order = requireOrder(workOrderId);
        WorkOrderStatus target = passed ? WorkOrderStatus.CLOSED : WorkOrderStatus.PENDING_REMEDIATION;
        WorkOrderStateMachine.assertTransition(order.status(), target);

        int updated = workOrderRepository.finishReview(workOrderId, target,
                reviewComment == null ? null : reviewComment.trim(), operator);
        if (updated == 0) {
            throw illegalTransitionAfterRecheck(workOrderId);
        }

        WorkflowAction action = passed ? WorkflowAction.PASS_REVIEW : WorkflowAction.RETURN_REMEDIATION;
        auditEventRepository.insert(AuditEvent.of(
                AuditEvent.ENTITY_WORK_ORDER,
                workOrderId,
                action.name(),
                WorkOrderStatus.PENDING_REVIEW.name(),
                target.name(),
                operator,
                reviewComment == null ? null : reviewComment.trim(),
                null));
    }

    public WorkOrderDetail detail(long workOrderId) {
        return workOrderRepository.findDetailById(workOrderId)
                .orElseThrow(() -> new WorkflowException(
                        WorkflowException.Code.WORK_ORDER_NOT_FOUND, "工单不存在或已被删除"));
    }

    /** status 为 null 时返回全部工单。 */
    public List<WorkOrderDetail> list(WorkOrderStatus status) {
        return workOrderRepository.findDetails(status);
    }

    /** 供 C 的 R7 判断是否允许进入建单表单。 */
    public boolean existsByHazard(long hazardId) {
        return workOrderRepository.existsByHazardId(hazardId);
    }

    /** 某张工单的处理时间线，供 C 的详情页展示。 */
    public List<AuditEvent> timeline(long workOrderId) {
        requireOrder(workOrderId);
        return auditEventRepository.findByEntity(AuditEvent.ENTITY_WORK_ORDER, workOrderId);
    }

    private WorkOrder requireOrder(long workOrderId) {
        return workOrderRepository.findById(workOrderId)
                .orElseThrow(() -> new WorkflowException(
                        WorkflowException.Code.WORK_ORDER_NOT_FOUND, "工单不存在或已被删除"));
    }

    /**
     * 条件更新失败说明状态已被其他请求改变，重新读取最新状态后给出准确文案。
     */
    private WorkflowException illegalTransitionAfterRecheck(long workOrderId) {
        return workOrderRepository.findById(workOrderId)
                .map(latest -> new WorkflowException(
                        WorkflowException.Code.ILLEGAL_TRANSITION,
                        "当前状态（%s）不允许该操作".formatted(latest.status().label())))
                .orElseGet(() -> new WorkflowException(
                        WorkflowException.Code.WORK_ORDER_NOT_FOUND, "工单不存在或已被删除"));
    }

    private static String toJson(Map<String, String> values) {
        try {
            return JSON.writeValueAsString(new LinkedHashMap<>(values));
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
