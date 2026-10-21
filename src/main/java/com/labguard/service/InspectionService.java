package com.labguard.service;

import com.labguard.model.AiResultSource;
import com.labguard.model.AnalysisStatus;
import com.labguard.model.AuditEvent;
import com.labguard.model.Hazard;
import com.labguard.model.Inspection;
import com.labguard.repository.AuditEventRepository;
import com.labguard.repository.HazardRepository;
import com.labguard.repository.InspectionRepository;
import com.labguard.workflow.WorkflowAction;
import com.labguard.workflow.WorkflowException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 巡检与 AI 分析结果的落库业务。
 * <p>
 * 只接收分析结果的字段（来源、原始响应、隐患列表），不依赖 A 模块的结果类型，
 * 由调用方从自己的分析结果中取值传入。一次巡检先以 PENDING 落库，
 * 分析完成后回写状态与原始响应，并把隐患批量保存为待确认。
 */
@Service
public class InspectionService {

    private static final String SYSTEM_OPERATOR = "system";

    private final InspectionRepository inspectionRepository;
    private final HazardRepository hazardRepository;
    private final AuditEventRepository auditEventRepository;

    public InspectionService(InspectionRepository inspectionRepository,
                             HazardRepository hazardRepository,
                             AuditEventRepository auditEventRepository) {
        this.inspectionRepository = inspectionRepository;
        this.hazardRepository = hazardRepository;
        this.auditEventRepository = auditEventRepository;
    }

    /** 记录一次巡检（图片已保存、尚未分析），返回巡检主键。 */
    @Transactional
    public long create(Inspection inspection) {
        long id = inspectionRepository.insert(inspection);
        auditEventRepository.insert(AuditEvent.of(
                AuditEvent.ENTITY_INSPECTION,
                id,
                WorkflowAction.CREATE_INSPECTION.name(),
                null,
                AnalysisStatus.PENDING.name(),
                inspection.inspector(),
                "创建巡检记录",
                null));
        return id;
    }

    /**
     * 保存分析成功的结果：回写 AI 原始响应，并把全部隐患按待确认状态落库。
     *
     * @param source      分析来源（真实 / 演示）
     * @param rawResponse 模型原始响应，可为 null
     * @param hazards     解析出的隐患，按传入顺序落库
     * @return 新隐患的主键，顺序与传入一致
     */
    @Transactional
    public List<Long> recordAnalysisResult(long inspectionId, AiResultSource source,
                                           String rawResponse, List<Hazard> hazards) {
        requireInspection(inspectionId);
        if (source == null) {
            throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT,
                    "分析来源不能为空");
        }
        inspectionRepository.updateAnalysisResult(inspectionId, AnalysisStatus.SUCCESS, rawResponse);

        List<Long> hazardIds = new ArrayList<>(hazards.size());
        for (Hazard hazard : hazards) {
            hazardIds.add(hazardRepository.insert(hazard.bindTo(inspectionId)));
        }

        auditEventRepository.insert(AuditEvent.of(
                AuditEvent.ENTITY_INSPECTION,
                inspectionId,
                WorkflowAction.ANALYZE.name(),
                AnalysisStatus.PENDING.name(),
                AnalysisStatus.SUCCESS.name(),
                SYSTEM_OPERATOR,
                "识别出 %d 条隐患（来源 %s）".formatted(hazardIds.size(), source),
                null));
        return List.copyOf(hazardIds);
    }

    /** 分析失败时回写状态与原始响应，保留失败现场。 */
    @Transactional
    public void recordAnalysisFailure(long inspectionId, String rawResponse) {
        requireInspection(inspectionId);
        inspectionRepository.updateAnalysisResult(inspectionId, AnalysisStatus.FAILED, rawResponse);
        auditEventRepository.insert(AuditEvent.of(
                AuditEvent.ENTITY_INSPECTION,
                inspectionId,
                WorkflowAction.ANALYZE.name(),
                AnalysisStatus.PENDING.name(),
                AnalysisStatus.FAILED.name(),
                SYSTEM_OPERATOR,
                "AI 分析失败",
                null));
    }

    public Inspection get(long inspectionId) {
        return requireInspection(inspectionId);
    }

    public List<Inspection> list() {
        return inspectionRepository.findAll();
    }

    public List<Hazard> listHazards(long inspectionId) {
        requireInspection(inspectionId);
        return hazardRepository.findByInspectionId(inspectionId);
    }

    /** 一次巡检的处理时间线，供 C 的详情页展示。 */
    public List<AuditEvent> timeline(long inspectionId) {
        requireInspection(inspectionId);
        return auditEventRepository.findByEntity(AuditEvent.ENTITY_INSPECTION, inspectionId);
    }

    private Inspection requireInspection(long inspectionId) {
        return inspectionRepository.findById(inspectionId)
                .orElseThrow(() -> new WorkflowException(
                        WorkflowException.Code.INVALID_ARGUMENT,
                        "巡检记录不存在：" + inspectionId));
    }
}
