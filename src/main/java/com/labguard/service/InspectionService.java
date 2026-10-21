package com.labguard.service;

import java.util.Optional;

/**
 * 巡检记录与分析编排。R3 先调用视觉服务，成功后才落库，
 * 保证失败时不会产生"看起来分析成功"的巡检数据。
 */
public interface InspectionService {

    /**
     * 保存图片、调用视觉分析并持久化巡检与隐患。
     *
     * @throws AiVisionException 视觉 API 未配置、调用失败或响应非法
     */
    long create(InspectionDraft draft);

    /** 分析结果页数据；巡检不存在时返回空。 */
    Optional<InspectionAnalysis> find(long inspectionId);
}
