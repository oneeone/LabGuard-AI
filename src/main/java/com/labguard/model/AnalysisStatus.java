package com.labguard.model;

/**
 * 巡检记录的分析状态。PENDING 表示已上传待分析，SUCCESS 表示已拿到结构化结果，
 * FAILED 表示视觉 API 未配置、调用失败或响应无法解析。
 */
public enum AnalysisStatus {

    PENDING("待分析"),
    SUCCESS("分析成功"),
    FAILED("分析失败");

    private final String label;

    AnalysisStatus(String label) {
        this.label = label;
    }

    /** 页面展示用中文文案。 */
    public String label() {
        return label;
    }
}
