package com.labguard.service;

import com.labguard.model.AiAnalysisResult;

/**
 * AI 视觉分析接口。业务层只依赖该接口，不直接依赖 HTTP 客户端。
 * 实现：RealAiVisionService（mode=real）、DemoAiVisionService（mode=demo）。
 */
public interface AiVisionService {
    AiAnalysisResult analyze(ImageInput image) throws AiVisionException;
}
