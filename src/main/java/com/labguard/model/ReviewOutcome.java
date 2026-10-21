package com.labguard.model;

/**
 * 整改照片复查的模型建议结果。任何取值都不直接关闭工单，最终结论由人工确认。
 */
public enum ReviewOutcome {
    PASSED("疑似已整改，建议人工确认后关闭"),
    FAILED("仍可见风险证据，建议退回继续整改"),
    INCONCLUSIVE("证据不足或画面无法判断，建议重新拍摄或现场核验");

    private final String label;

    ReviewOutcome(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
