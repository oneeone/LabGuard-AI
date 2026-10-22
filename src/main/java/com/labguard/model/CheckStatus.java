package com.labguard.model;

/** 单个核验点（原证据）的判定结果。 */
public enum CheckStatus {
    RESOLVED("已解决"),
    UNRESOLVED("仍存在"),
    INCONCLUSIVE("无法判断");

    private final String label;

    CheckStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
