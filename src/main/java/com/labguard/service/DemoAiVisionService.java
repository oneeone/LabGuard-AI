package com.labguard.service;

import com.labguard.model.AiAnalysisResult;
import com.labguard.model.AiResultSource;
import com.labguard.model.Hazard;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 显式演示实现：不读取图片内容、不发起任何网络请求，结果固定标记为 DEMO。
 * 真实 API 失败时绝不会切换到这里（RealAiVisionService 只抛异常）。
 */
@Service
@ConditionalOnProperty(prefix = "labguard.ai", name = "mode", havingValue = "demo", matchIfMissing = true)
public class DemoAiVisionService implements AiVisionService {

    @Override
    public AiAnalysisResult analyze(ImageInput image) {
        Hazard hazard = new Hazard(
                "通道疑似被物品阻挡（演示样例）",
                "中",
                "图片中的通道区域（示例）",
                "这是固定的演示数据，未调用真实视觉 API，不代表系统分析了上传图片。",
                0.90,
                "演示样例：仅用于验证界面与流程，需现场核实。",
                "请现场核实通道是否被阻挡；如属实，移除障碍并保持通道畅通。",
                true,
                true
        );
        return new AiAnalysisResult(List.of(hazard), AiResultSource.DEMO, "demo-sample", Instant.now(), null);
    }
}
