package com.labguard.model;

import java.time.Instant;
import java.util.List;

/**
 * 一次视觉分析的结果。source 必须随结果一起流转，页面和数据记录都要能读取。
 */
public record AiAnalysisResult(
        List<HazardItem> hazards,
        AiResultSource source,
        String model,
        Instant analyzedAt,
        String rawResponse
) {
    public AiAnalysisResult {
        hazards = hazards == null ? List.of() : List.copyOf(hazards);
    }

    public boolean demo() {
        return source == AiResultSource.DEMO;
    }

    public String sourceLabel() {
        return demo() ? "演示数据：未调用真实视觉 API" : "真实 AI 结果";
    }
}
