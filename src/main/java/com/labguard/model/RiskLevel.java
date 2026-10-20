package com.labguard.model;

/**
 * 风险等级建议。页面统一展示为"建议风险等级"，最终等级由巡检人员确认或调整。
 */
public enum RiskLevel {

    HIGH("高"),
    MEDIUM("中"),
    LOW("低");

    private final String label;

    RiskLevel(String label) {
        this.label = label;
    }

    /** 页面展示用中文文案。 */
    public String label() {
        return label;
    }
}
