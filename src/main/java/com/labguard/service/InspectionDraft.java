package com.labguard.service;

import java.time.LocalDateTime;

/**
 * 新建巡检的提交内容。图片以字节数组传入，由服务层负责落盘与分析，
 * 页面层不接触文件路径规则。
 */
public record InspectionDraft(
        String labName,
        String inspector,
        LocalDateTime inspectedAt,
        String note,
        byte[] imageBytes,
        String filename
) {
}
