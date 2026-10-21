package com.labguard.controller;

import com.labguard.model.HazardStatus;
import com.labguard.model.RiskLevel;
import com.labguard.service.HazardEditCommand;
import com.labguard.service.HazardService;
import com.labguard.workflow.WorkflowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 附录 A3：确认、修改、驳回。合法性判断在 HazardService，控制器只负责把结果
 * 以闪现消息带回分析结果页。
 */
@WebMvcTest(controllers = HazardReviewController.class)
class HazardReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HazardService hazardService;

    @Test
    @DisplayName("A3 action=CONFIRM：调用确认并重定向回分析页")
    void confirmRedirectsToAnalysis() throws Exception {
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));

        mockMvc.perform(post("/hazards/{id}/confirm", TestFixtures.HAZARD_ID)
                        .param("action", "CONFIRM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inspections/7/analysis"))
                .andExpect(flash().attribute("reviewMessage", containsString("风险已确认")));

        verify(hazardService).confirm(eq(TestFixtures.HAZARD_ID), anyString());
    }

    @Test
    @DisplayName("A3 action=EDIT：把修改后的内容交给服务并标记确认")
    void editPassesModifiedValues() throws Exception {
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));

        mockMvc.perform(post("/hazards/{id}/confirm", TestFixtures.HAZARD_ID)
                        .param("action", "EDIT")
                        .param("category", "电源使用不规范")
                        .param("riskLevel", "HIGH")
                        .param("location", "通风橱旁")
                        .param("evidence", "插排串联使用")
                        .param("suggestedAction", "更换固定插座"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inspections/7/analysis"))
                .andExpect(flash().attribute("reviewMessage", containsString("已修改")));

        ArgumentCaptor<HazardEditCommand> captor = ArgumentCaptor.forClass(HazardEditCommand.class);
        verify(hazardService).edit(eq(TestFixtures.HAZARD_ID), captor.capture(), anyString());
        assertThat(captor.getValue()).isEqualTo(new HazardEditCommand(
                "电源使用不规范", RiskLevel.HIGH, "通风橱旁", "插排串联使用", "更换固定插座"));
    }

    @Test
    @DisplayName("A3 action=REJECT 无 reviewNote：服务拒绝，错误带回原页")
    void rejectWithoutReasonFails() throws Exception {
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));
        willThrow(new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT, "驳回时必须填写驳回理由"))
                .given(hazardService).reject(eq(TestFixtures.HAZARD_ID), isNull(), anyString());

        mockMvc.perform(post("/hazards/{id}/confirm", TestFixtures.HAZARD_ID)
                        .param("action", "REJECT"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inspections/7/analysis"))
                .andExpect(flash().attribute("reviewError", containsString("驳回理由")));
    }

    @Test
    @DisplayName("A3 已确认的隐患再次确认：被拒绝并提示当前状态")
    void confirmConfirmedHazardRejected() throws Exception {
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.CONFIRMED));
        willThrow(new WorkflowException(WorkflowException.Code.ILLEGAL_TRANSITION,
                "当前状态（已确认）不允许该操作"))
                .given(hazardService).confirm(eq(TestFixtures.HAZARD_ID), anyString());

        mockMvc.perform(post("/hazards/{id}/confirm", TestFixtures.HAZARD_ID)
                        .param("action", "CONFIRM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inspections/7/analysis"))
                .andExpect(flash().attribute("reviewError", containsString("不允许该操作")));
    }

    @Test
    @DisplayName("A3 未选择动作：提示选择确认、修改或驳回")
    void missingActionIsRejected() throws Exception {
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));

        mockMvc.perform(post("/hazards/{id}/confirm", TestFixtures.HAZARD_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("reviewError", containsString("请选择确认、修改或驳回")));

        verify(hazardService, never()).confirm(anyLong(), anyString());
    }

    @Test
    @DisplayName("A3 隐患不存在：回到上传页并提示")
    void unknownHazardReturnsToUpload() throws Exception {
        given(hazardService.get(999L))
                .willThrow(new WorkflowException(WorkflowException.Code.HAZARD_NOT_FOUND, "隐患不存在或已被删除"));

        mockMvc.perform(post("/hazards/{id}/confirm", 999L).param("action", "CONFIRM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inspections/new"))
                .andExpect(flash().attribute("reviewError", containsString("隐患不存在")));
    }

    @Test
    @DisplayName("A3 驳回成功：闪现消息提示可重新上传")
    void rejectWithReasonSucceeds() throws Exception {
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));

        mockMvc.perform(post("/hazards/{id}/confirm", TestFixtures.HAZARD_ID)
                        .param("action", "REJECT")
                        .param("reviewNote", "现场已核实，临时堆放不影响通行"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("reviewMessage", containsString("已驳回")));

        verify(hazardService).reject(eq(TestFixtures.HAZARD_ID),
                eq("现场已核实，临时堆放不影响通行"), anyString());
    }
}
