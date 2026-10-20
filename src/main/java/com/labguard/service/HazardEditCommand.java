package com.labguard.service;

import com.labguard.model.RiskLevel;

/**
 * 人工修改风险时提交的内容，对应 C 模块契约 F2 表单中 EDIT 分支的字段。
 * 修改后风险等级来源标记为 MANUAL。
 */
public record HazardEditCommand(
        String category,
        RiskLevel riskLevel,
        String location,
        String evidence,
        String suggestedAction
) {
}
