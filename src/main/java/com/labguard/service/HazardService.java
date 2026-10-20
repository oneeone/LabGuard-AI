package com.labguard.service;

import com.labguard.model.AuditEvent;
import com.labguard.model.Hazard;
import com.labguard.model.HazardStatus;
import com.labguard.model.RiskLevelSource;
import com.labguard.repository.AuditEventRepository;
import com.labguard.repository.HazardRepository;
import com.labguard.workflow.WorkflowAction;
import com.labguard.workflow.WorkflowException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 隐患人工确认业务。确认、修改、驳回都必须从 PENDING_CONFIRM 出发，
 * 重复提交会被条件更新拒绝。每次成功变更写入审计记录。
 */
@Service
public class HazardService {

    private final HazardRepository hazardRepository;
    private final AuditEventRepository auditEventRepository;

    public HazardService(HazardRepository hazardRepository, AuditEventRepository auditEventRepository) {
        this.hazardRepository = hazardRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /** 直接确认 AI 给出的结果。 */
    @Transactional
    public void confirm(long hazardId, String operator) {
        Hazard current = requireHazard(hazardId);
        if (current.status() != HazardStatus.PENDING_CONFIRM) {
            throw illegalTransition(current.status());
        }
        applyConfirm(current, operator, WorkflowAction.CONFIRM_HAZARD, "确认风险");
    }

    /** 修改风险内容后确认，风险等级来源标记为人工调整。 */
    @Transactional
    public void edit(long hazardId, HazardEditCommand command, String operator) {
        Hazard current = requireHazard(hazardId);
        if (current.status() != HazardStatus.PENDING_CONFIRM) {
            throw illegalTransition(current.status());
        }
        if (isBlank(command.category()) || command.riskLevel() == null || isBlank(command.location())
                || isBlank(command.evidence()) || isBlank(command.suggestedAction())) {
            throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT,
                    "修改风险时类别、风险等级、位置、可见证据和建议措施均为必填");
        }
        Hazard edited = new Hazard(
                current.id(),
                current.inspectionId(),
                command.category().trim(),
                command.riskLevel(),
                RiskLevelSource.MANUAL,
                command.location().trim(),
                command.evidence().trim(),
                current.confidence(),
                current.uncertainty(),
                command.suggestedAction().trim(),
                current.needsHumanReview(),
                HazardStatus.PENDING_CONFIRM,
                null,
                null,
                null,
                current.createdAt());
        applyConfirm(edited, operator, WorkflowAction.EDIT_HAZARD, "修改并确认风险");
    }

    /** 驳回 AI 结果，必须说明理由。 */
    @Transactional
    public void reject(long hazardId, String reviewNote, String operator) {
        Hazard current = requireHazard(hazardId);
        if (current.status() != HazardStatus.PENDING_CONFIRM) {
            throw illegalTransition(current.status());
        }
        if (isBlank(reviewNote)) {
            throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT, "驳回时必须填写驳回理由");
        }
        LocalDateTime now = LocalDateTime.now();
        int updated = hazardRepository.reject(hazardId, reviewNote.trim(), operator, now);
        if (updated == 0) {
            throw illegalTransition(requireHazard(hazardId).status());
        }
        auditEventRepository.insert(AuditEvent.of(
                AuditEvent.ENTITY_HAZARD,
                hazardId,
                WorkflowAction.REJECT_HAZARD.name(),
                HazardStatus.PENDING_CONFIRM.name(),
                HazardStatus.REJECTED.name(),
                operator,
                reviewNote.trim(),
                null));
    }

    public Hazard get(long hazardId) {
        return requireHazard(hazardId);
    }

    public List<Hazard> listByInspection(long inspectionId) {
        return hazardRepository.findByInspectionId(inspectionId);
    }

    private void applyConfirm(Hazard hazard, String operator, WorkflowAction action, String comment) {
        LocalDateTime now = LocalDateTime.now();
        int updated = hazardRepository.confirm(hazard, operator, now);
        if (updated == 0) {
            throw illegalTransition(requireHazard(hazard.id()).status());
        }
        auditEventRepository.insert(AuditEvent.of(
                AuditEvent.ENTITY_HAZARD,
                hazard.id(),
                action.name(),
                HazardStatus.PENDING_CONFIRM.name(),
                HazardStatus.CONFIRMED.name(),
                operator,
                comment,
                null));
    }

    private Hazard requireHazard(long hazardId) {
        return hazardRepository.findById(hazardId)
                .orElseThrow(() -> new WorkflowException(
                        WorkflowException.Code.HAZARD_NOT_FOUND, "隐患不存在或已被删除"));
    }

    private static WorkflowException illegalTransition(HazardStatus status) {
        return new WorkflowException(WorkflowException.Code.ILLEGAL_TRANSITION,
                "当前状态（%s）不允许该操作".formatted(status.label()));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
