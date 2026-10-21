package com.labguard.model;

/**
 * 工单详情视图：工单 + 关联隐患 + 关联巡检。
 * 页面渲染所需字段一次取齐，模板层不再额外查询数据库。
 */
public record WorkOrderDetail(
        WorkOrder order,
        Hazard hazard,
        Inspection inspection
) {

    public String labName() {
        return inspection.labName();
    }

    public String originalImagePath() {
        return inspection.imagePath();
    }
}
