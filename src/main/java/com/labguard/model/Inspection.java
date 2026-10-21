package com.labguard.model;

import java.time.LocalDateTime;

/**
 * 巡检记录。保存原始图片路径、分析来源与 AI 原始响应，是隐患的上级对象。
 * 时间字段一律以 ISO-8601 文本落库，由仓储层负责转换。
 */
public record Inspection(
        long id,
        String labName,
        String inspector,
        LocalDateTime inspectedAt,
        String imagePath,
        AiResultSource analysisSource,
        AnalysisStatus analysisStatus,
        String aiRawResponse,
        String note,
        LocalDateTime createdAt
) {

    /** 新建巡检（图片已保存、尚未分析）时使用，id 由数据库回填。 */
    public static Inspection pending(String labName, String inspector, LocalDateTime inspectedAt,
                                     String imagePath, AiResultSource analysisSource, String note) {
        return new Inspection(0L, labName, inspector, inspectedAt, imagePath,
                analysisSource, AnalysisStatus.PENDING, null, note, LocalDateTime.now());
    }
}
