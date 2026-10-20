package com.labguard.web.form;

import com.labguard.model.RiskLevel;

/**
 * F2 人工确认表单。action 决定后续使用哪些字段：
 * CONFIRM 只确认，EDIT 携带修改后的风险内容，REJECT 必填驳回理由。
 */
public class HazardReviewForm {

    private String action;
    private String category;
    private RiskLevel riskLevel;
    private String location;
    private String evidence;
    private String suggestedAction;
    private String reviewNote;

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getEvidence() {
        return evidence;
    }

    public void setEvidence(String evidence) {
        this.evidence = evidence;
    }

    public String getSuggestedAction() {
        return suggestedAction;
    }

    public void setSuggestedAction(String suggestedAction) {
        this.suggestedAction = suggestedAction;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }
}
