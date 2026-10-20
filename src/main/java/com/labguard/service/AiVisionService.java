package com.labguard.service;

import com.labguard.model.AiAnalysisResult;
import com.labguard.model.RemediationContext;
import com.labguard.model.RemediationReview;

/**
 * AI 视觉分析接口。业务层只依赖该接口，不直接依赖 HTTP 客户端。
 * 实现：RealAiVisionService（mode=real）、DemoAiVisionService（mode=demo）。
 * <p>
 * 边界：本接口只做分析并返回结构化结果，绝不修改工单状态；
 * 状态流转由成员 B 的工作流服务基于结果人工决策。
 */
public interface AiVisionService {

    AiAnalysisResult analyze(ImageInput image) throws AiVisionException;

    /**
     * 整改照片复查：结合原风险上下文分析整改后的图片。
     * 结果恒为 needsHumanReview=true；调用失败抛 AiVisionException，不会被当作通过。
     */
    RemediationReview reviewRemediation(ImageInput remediationImage, RemediationContext context)
            throws AiVisionException;
}
