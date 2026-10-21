package com.labguard.service;

import com.labguard.model.Hazard;
import com.labguard.model.Inspection;

import java.util.List;

/**
 * 分析结果页所需的巡检记录与隐患列表。字段一次取齐，页面层不再回查数据库。
 */
public record InspectionAnalysis(
        Inspection inspection,
        List<Hazard> hazards
) {

    public InspectionAnalysis {
        hazards = hazards == null ? List.of() : List.copyOf(hazards);
    }
}
