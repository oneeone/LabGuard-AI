package com.labguard.service;

import com.labguard.model.AiAnalysisResult;
import com.labguard.model.AiResultSource;
import com.labguard.model.CheckStatus;
import com.labguard.model.Comparability;
import com.labguard.model.EvidenceCheck;
import com.labguard.model.HazardItem;
import com.labguard.model.RemediationContext;
import com.labguard.model.RemediationReview;
import com.labguard.model.ReviewOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 显式演示实现：不读取图片内容、不发起任何网络请求，结果固定标记为 DEMO。
 * 真实 API 失败时绝不会切换到这里（RealAiVisionService 只抛异常）。
 */
@Service
@ConditionalOnProperty(prefix = "labguard.ai", name = "mode", havingValue = "demo", matchIfMissing = true)
public class DemoAiVisionService implements AiVisionService {

    @Override
    public AiAnalysisResult analyze(ImageInput image) {
        HazardItem hazard = new HazardItem(
                "通道疑似被物品阻挡（演示样例）",
                "中",
                "图片中的通道区域（示例）",
                "这是固定的演示数据，未调用真实视觉 API，不代表系统分析了上传图片。",
                0.90,
                "演示样例：仅用于验证界面与流程，需现场核实。",
                "请现场核实通道是否被阻挡；如属实，移除障碍并保持通道畅通。",
                true,
                true
        );
        return new AiAnalysisResult(List.of(hazard), AiResultSource.DEMO, "demo-sample", Instant.now(), null);
    }

    @Override
    public RemediationReview reviewRemediation(ImageInput originalImage, ImageInput remediationImage,
                                               RemediationContext context) {
        if (originalImage == null || remediationImage == null) {
            throw new AiVisionException(AiErrorType.REQUEST_FAILED, "复查必须同时提供原始照片和整改后照片");
        }
        if (context == null) {
            throw new AiVisionException(AiErrorType.REQUEST_FAILED, "复查上下文不能为空");
        }
        // 演示数据无法验证真实整改效果：逐点固定 INCONCLUSIVE，整体结论自然不会被判为通过。
        EvidenceCheck demoCheck = new EvidenceCheck(
                context.originalEvidence().isBlank() ? context.originalLocation() : context.originalEvidence(),
                CheckStatus.INCONCLUSIVE,
                "",
                "演示模式未调用真实视觉 API，无法核验该证据点。");
        return new RemediationReview(
                ReviewOutcome.INCONCLUSIVE,
                "演示数据：未调用真实视觉 API，不能据此判断整改效果。",
                List.of(),
                "演示样例：仅用于验证复查流程，需人工现场确认。",
                true,
                "请接入真实视觉 API 或由人员现场核验工单 " + context.orderNo() + " 的整改结果。",
                AiResultSource.DEMO,
                "demo-sample",
                Instant.now(),
                null,
                List.of(demoCheck),
                Comparability.PARTIAL
        );
    }
}
