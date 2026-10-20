package com.labguard.model;

/**
 * 风险等级的来源：RULE 表示由规则模块依据隐患类别给出，MANUAL 表示巡检人员人工调整。
 */
public enum RiskLevelSource {

    RULE("规则建议"),
    MANUAL("人工调整");

    private final String label;

    RiskLevelSource(String label) {
        this.label = label;
    }

    /** 页面展示用中文文案。 */
    public String label() {
        return label;
    }
}
