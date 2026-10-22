package com.labguard.model;

/**
 * 整改前后照片的可比性：只有同一视角、同一区域才允许给出通过结论。
 * NOT_COMPARABLE 会强制把整体结论降级为 INCONCLUSIVE。
 */
public enum Comparability {
    COMPARABLE("视角可比"),
    PARTIAL("部分可比"),
    NOT_COMPARABLE("视角不可比，无法对照");

    private final String label;

    Comparability(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
