package com.labguard.controller;

import com.labguard.model.AiResultSource;
import com.labguard.model.AnalysisStatus;
import com.labguard.model.Hazard;
import com.labguard.model.HazardStatus;
import com.labguard.model.Inspection;
import com.labguard.model.RiskLevel;
import com.labguard.model.RiskLevelSource;
import com.labguard.model.WorkOrder;
import com.labguard.model.WorkOrderDetail;
import com.labguard.model.WorkOrderStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MVC 测试共用的样例数据。只构造页面需要的字段，避免每个测试重复 16 参数构造。
 */
final class TestFixtures {

    static final LocalDateTime BASE_TIME = LocalDateTime.of(2026, 10, 20, 10, 0);
    static final long INSPECTION_ID = 7L;
    static final long HAZARD_ID = 5L;
    static final long WORK_ORDER_ID = 12L;

    private TestFixtures() {
    }

    static Inspection inspection(AiResultSource source) {
        return new Inspection(INSPECTION_ID, "化学实验楼 A302", "张三", BASE_TIME,
                "/uploads/original.jpg", source, AnalysisStatus.SUCCESS, "{}", "巡检备注", BASE_TIME);
    }

    static Hazard hazard(HazardStatus status) {
        return hazard(status, RiskLevel.MEDIUM, 0.72d, false);
    }

    static Hazard hazard(HazardStatus status, RiskLevel riskLevel, Double confidence, boolean needsHumanReview) {
        return new Hazard(HAZARD_ID, INSPECTION_ID, "通道被遮挡", riskLevel, RiskLevelSource.RULE,
                "实验室门口", "纸箱堆放在安全出口前", confidence, null,
                "移除纸箱；保持通道畅通", needsHumanReview, status, null, null, null, BASE_TIME);
    }

    static WorkOrder workOrder(WorkOrderStatus status) {
        boolean submitted = status != WorkOrderStatus.PENDING_REMEDIATION;
        boolean closed = status == WorkOrderStatus.CLOSED;
        return new WorkOrder(WORK_ORDER_ID, HAZARD_ID, "李四", LocalDate.now().plusDays(3), status,
                "尽快整改",
                submitted ? "已清理纸箱" : null,
                submitted ? "/uploads/after.jpg" : null,
                closed ? "复查合格" : null,
                closed ? "复查员" : null,
                closed ? BASE_TIME.plusDays(1) : null,
                BASE_TIME, BASE_TIME);
    }

    static WorkOrderDetail detail(WorkOrderStatus status) {
        return new WorkOrderDetail(workOrder(status),
                hazard(HazardStatus.CONFIRMED), inspection(AiResultSource.DEMO));
    }
}
