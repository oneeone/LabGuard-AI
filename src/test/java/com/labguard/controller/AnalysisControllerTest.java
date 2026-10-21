package com.labguard.controller;

import com.labguard.model.AiResultSource;
import com.labguard.model.Hazard;
import com.labguard.model.HazardStatus;
import com.labguard.model.Inspection;
import com.labguard.model.RiskLevel;
import com.labguard.service.InspectionAnalysis;
import com.labguard.service.InspectionService;
import com.labguard.service.WorkOrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 附录 A2：分析结果页。来源标识、置信度缺失与人工核验提示均由 HazardCard 计算，
 * 这里验证模板最终呈现。
 */
@WebMvcTest(controllers = AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InspectionService inspectionService;

    @MockitoBean
    private WorkOrderService workOrderService;

    @Test
    @DisplayName("A2 正常结果：渲染类别、证据与建议措施")
    void rendersHazardFields() throws Exception {
        givenAnalysis(TestFixtures.inspection(AiResultSource.REAL), TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));

        mockMvc.perform(get("/inspections/{id}/analysis", TestFixtures.INSPECTION_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("analysis"))
                .andExpect(content().string(containsString("通道被遮挡")))
                .andExpect(content().string(containsString("纸箱堆放在安全出口前")))
                .andExpect(content().string(containsString("移除纸箱")))
                .andExpect(content().string(containsString("规则建议")));
    }

    @Test
    @DisplayName("A2 source=DEMO：页面含演示数据标识")
    void demoSourceIsVisible() throws Exception {
        givenAnalysis(TestFixtures.inspection(AiResultSource.DEMO), TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));

        mockMvc.perform(get("/inspections/{id}/analysis", TestFixtures.INSPECTION_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("演示数据，非真实识别结果")));
    }

    @Test
    @DisplayName("A2 source=REAL：页面不含演示数据标识")
    void realSourceHasNoDemoBanner() throws Exception {
        givenAnalysis(TestFixtures.inspection(AiResultSource.REAL), TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));

        mockMvc.perform(get("/inspections/{id}/analysis", TestFixtures.INSPECTION_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("真实 AI 分析")))
                .andExpect(content().string(not(containsString("演示数据"))));
    }

    @Test
    @DisplayName("A2 confidence=null：显示未提供，不显示 0%")
    void missingConfidenceShowsNotProvided() throws Exception {
        givenAnalysis(TestFixtures.inspection(AiResultSource.REAL),
                TestFixtures.hazard(HazardStatus.PENDING_CONFIRM, RiskLevel.MEDIUM, null, false));

        mockMvc.perform(get("/inspections/{id}/analysis", TestFixtures.INSPECTION_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("未提供")))
                .andExpect(content().string(not(containsString("0%"))));
    }

    @Test
    @DisplayName("A2 needsHumanReview=true：显示需人工现场核实警示")
    void needsHumanReviewWarning() throws Exception {
        givenAnalysis(TestFixtures.inspection(AiResultSource.REAL),
                TestFixtures.hazard(HazardStatus.PENDING_CONFIRM, RiskLevel.HIGH, 0.91d, true));

        mockMvc.perform(get("/inspections/{id}/analysis", TestFixtures.INSPECTION_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("需人工现场核实")));
    }

    @Test
    @DisplayName("A2 置信度低于 0.5：自动提示人工核实并标为低档")
    void lowConfidenceForcesHumanReview() throws Exception {
        givenAnalysis(TestFixtures.inspection(AiResultSource.REAL),
                TestFixtures.hazard(HazardStatus.PENDING_CONFIRM, RiskLevel.LOW, 0.32d, false));

        mockMvc.perform(get("/inspections/{id}/analysis", TestFixtures.INSPECTION_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("低（32%）")))
                .andExpect(content().string(containsString("需人工现场核实")));
    }

    @Test
    @DisplayName("S1 缺失字段：显示未分类 / 待评估 / 无可见证据 / 暂无建议")
    void missingFieldsUseContractFallbacks() throws Exception {
        Hazard empty = new Hazard(TestFixtures.HAZARD_ID, TestFixtures.INSPECTION_ID, null, null, null,
                null, null, null, null, null, false, HazardStatus.PENDING_CONFIRM,
                null, null, null, TestFixtures.BASE_TIME);
        givenAnalysis(TestFixtures.inspection(AiResultSource.REAL), empty);

        mockMvc.perform(get("/inspections/{id}/analysis", TestFixtures.INSPECTION_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("未分类")))
                .andExpect(content().string(containsString("待评估")))
                .andExpect(content().string(containsString("位置未说明")))
                .andExpect(content().string(containsString("无可见证据")))
                .andExpect(content().string(containsString("暂无建议")))
                .andExpect(content().string(containsString("需人工现场核实")));
    }

    @Test
    @DisplayName("R4 巡检不存在：返回 404")
    void unknownInspectionReturns404() throws Exception {
        given(inspectionService.find(404L)).willReturn(Optional.empty());

        mockMvc.perform(get("/inspections/{id}/analysis", 404L))
                .andExpect(status().isNotFound());
    }

    private void givenAnalysis(Inspection inspection, Hazard hazard) {
        given(inspectionService.find(TestFixtures.INSPECTION_ID))
                .willReturn(Optional.of(new InspectionAnalysis(inspection, List.of(hazard))));
        given(workOrderService.existsByHazard(anyLong())).willReturn(false);
    }
}
