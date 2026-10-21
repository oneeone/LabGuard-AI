package com.labguard.service;

import com.labguard.model.AiAnalysisResult;
import com.labguard.model.Hazard;
import com.labguard.model.HazardItem;
import com.labguard.model.Inspection;
import com.labguard.model.RiskLevel;
import com.labguard.repository.HazardRepository;
import com.labguard.repository.InspectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 上传到分析结果的最小流程编排：视觉分析 → 保存图片 → 写入巡检与隐患。
 * 视觉分析放在落库之前，失败时直接抛给页面层，不写入任何"分析成功"数据。
 * <p>
 * 集成适配：AI 输出是 {@link HazardItem}（分析 DTO，不持有巡检编号与确认状态），
 * 落库前统一转换为 B 的 {@link Hazard} 实体（初始 PENDING_CONFIRM，等级建议来自模型，
 * "待评估"记为 UNKNOWN），并委托 {@link InspectionRecordService} 写入巡检与审计时间线。
 */
@Service
public class DefaultInspectionService implements InspectionService {

    private final AiVisionService aiVisionService;
    private final InspectionRecordService inspectionRecordService;
    private final InspectionRepository inspectionRepository;
    private final HazardRepository hazardRepository;
    private final LocalFileStorage fileStorage;

    public DefaultInspectionService(AiVisionService aiVisionService,
                                    InspectionRecordService inspectionRecordService,
                                    InspectionRepository inspectionRepository,
                                    HazardRepository hazardRepository,
                                    LocalFileStorage fileStorage) {
        this.aiVisionService = aiVisionService;
        this.inspectionRecordService = inspectionRecordService;
        this.inspectionRepository = inspectionRepository;
        this.hazardRepository = hazardRepository;
        this.fileStorage = fileStorage;
    }

    @Override
    @Transactional
    public long create(InspectionDraft draft) {
        AiAnalysisResult result = aiVisionService.analyze(
                ImageInput.of(draft.imageBytes(), draft.filename()));
        String imagePath = fileStorage.store(draft.imageBytes(), draft.filename());

        long inspectionId = inspectionRecordService.create(Inspection.pending(
                draft.labName(), draft.inspector(), draft.inspectedAt(), imagePath,
                result.source(), draft.note()));
        List<Hazard> hazards = result.hazards().stream()
                .map(item -> toEntity(item))
                .toList();
        inspectionRecordService.recordAnalysisResult(
                inspectionId, result.source(), result.rawResponse(), hazards);
        return inspectionId;
    }

    @Override
    public Optional<InspectionAnalysis> find(long inspectionId) {
        return inspectionRepository.findById(inspectionId)
                .map(inspection -> new InspectionAnalysis(
                        inspection, hazardRepository.findByInspectionId(inspectionId)));
    }

    /** AI 分析结果 → 隐患实体：落库时由 InspectionRecordService 绑定巡检编号并置为待确认。 */
    private static Hazard toEntity(HazardItem item) {
        return Hazard.fromAi(0L, item.category(), mapRiskLevel(item.riskLevel()),
                item.location(), item.evidence(), item.confidence(),
                item.uncertainty(), item.suggestedAction(), item.needsHumanReview());
    }

    private static RiskLevel mapRiskLevel(String label) {
        return switch (label == null ? "" : label.trim()) {
            case "高" -> RiskLevel.HIGH;
            case "中" -> RiskLevel.MEDIUM;
            case "低" -> RiskLevel.LOW;
            default -> RiskLevel.UNKNOWN;
        };
    }
}
