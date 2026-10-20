package com.labguard.service;

import com.labguard.model.AiAnalysisResult;
import com.labguard.model.AnalysisStatus;
import com.labguard.model.Hazard;
import com.labguard.model.Inspection;
import com.labguard.repository.HazardRepository;
import com.labguard.repository.InspectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 上传到分析结果的最小流程编排：视觉分析 → 保存图片 → 写入巡检与隐患。
 * 视觉分析放在落库之前，失败时直接抛给页面层，不写入任何"分析成功"数据。
 */
@Service
public class DefaultInspectionService implements InspectionService {

    private final AiVisionService aiVisionService;
    private final InspectionRepository inspectionRepository;
    private final HazardRepository hazardRepository;
    private final LocalFileStorage fileStorage;

    public DefaultInspectionService(AiVisionService aiVisionService,
                                    InspectionRepository inspectionRepository,
                                    HazardRepository hazardRepository,
                                    LocalFileStorage fileStorage) {
        this.aiVisionService = aiVisionService;
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

        long inspectionId = inspectionRepository.insert(Inspection.pending(
                draft.labName(), draft.inspector(), draft.inspectedAt(), imagePath,
                result.source(), draft.note()));
        for (Hazard hazard : result.hazards()) {
            hazardRepository.insert(bindInspection(hazard, inspectionId));
        }
        inspectionRepository.updateAnalysisResult(inspectionId, AnalysisStatus.SUCCESS, result.rawResponse());
        return inspectionId;
    }

    @Override
    public Optional<InspectionAnalysis> find(long inspectionId) {
        return inspectionRepository.findById(inspectionId)
                .map(inspection -> new InspectionAnalysis(
                        inspection, hazardRepository.findByInspectionId(inspectionId)));
    }

    /** AI 层不持有巡检编号，落库前统一绑定。 */
    private static Hazard bindInspection(Hazard hazard, long inspectionId) {
        return new Hazard(
                hazard.id(),
                inspectionId,
                hazard.category(),
                hazard.riskLevel(),
                hazard.riskLevelSource(),
                hazard.location(),
                hazard.evidence(),
                hazard.confidence(),
                hazard.uncertainty(),
                hazard.suggestedAction(),
                hazard.needsHumanReview(),
                hazard.status(),
                hazard.reviewNote(),
                hazard.confirmedBy(),
                hazard.confirmedAt(),
                hazard.createdAt());
    }
}
