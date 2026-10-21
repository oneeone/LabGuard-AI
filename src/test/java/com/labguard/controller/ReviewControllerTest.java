package com.labguard.controller;

import com.labguard.model.WorkOrderStatus;
import com.labguard.service.HazardService;
import com.labguard.service.LocalFileStorage;
import com.labguard.service.WorkOrderService;
import com.labguard.service.WorkOrderTimelineService;
import com.labguard.workflow.WorkflowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
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
 * 附录 A6：复查。通过关闭工单，退回必须填写意见并回到待整改。
 */
@WebMvcTest(controllers = WorkOrderController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkOrderService workOrderService;

    @MockitoBean
    private HazardService hazardService;

    @MockitoBean
    private LocalFileStorage fileStorage;

    @MockitoBean
    private WorkOrderTimelineService timelineService;

    @Test
    @DisplayName("A6 action=PASS：工单关闭")
    void passClosesWorkOrder() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));

        mockMvc.perform(post("/workorders/{id}/review", TestFixtures.WORK_ORDER_ID)
                        .param("action", "PASS")
                        .param("reviewComment", "复查合格"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/workorders/12"))
                .andExpect(flash().attribute("detailMessage", containsString("复查通过")));

        verify(workOrderService).review(TestFixtures.WORK_ORDER_ID, true, "复查合格", "复查员");
    }

    @Test
    @DisplayName("A6 action=RETURN 无意见：校验失败，不调用服务")
    void returnWithoutCommentRejected() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));

        mockMvc.perform(post("/workorders/{id}/review", TestFixtures.WORK_ORDER_ID)
                        .param("action", "RETURN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/workorders/12"))
                .andExpect(flash().attribute("detailError", containsString("必须填写复查意见")));

        verify(workOrderService, never()).review(anyLong(), anyBoolean(), any(), anyString());
    }

    @Test
    @DisplayName("A6 action=RETURN 有意见：退回待整改并持久化意见")
    void returnWithCommentGoesBackToRemediation() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));

        mockMvc.perform(post("/workorders/{id}/review", TestFixtures.WORK_ORDER_ID)
                        .param("action", "RETURN")
                        .param("reviewComment", "通道仍有杂物"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("detailMessage", containsString("已退回整改")));

        verify(workOrderService).review(TestFixtures.WORK_ORDER_ID, false, "通道仍有杂物", "复查员");
    }

    @Test
    @DisplayName("A6 已关闭工单再操作：拒绝且提示当前状态")
    void closedWorkOrderCannotBeReviewed() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.CLOSED));

        mockMvc.perform(post("/workorders/{id}/review", TestFixtures.WORK_ORDER_ID)
                        .param("action", "PASS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("detailError", containsString("当前状态（已关闭）不允许该操作")));

        verify(workOrderService, never()).review(anyLong(), anyBoolean(), any(), anyString());
    }

    @Test
    @DisplayName("A6 PASS 时清理空白意见为 null")
    void blankCommentBecomesNull() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));

        mockMvc.perform(post("/workorders/{id}/review", TestFixtures.WORK_ORDER_ID)
                        .param("action", "PASS")
                        .param("reviewComment", "   "))
                .andExpect(status().is3xxRedirection());

        verify(workOrderService).review(eq(TestFixtures.WORK_ORDER_ID), eq(true), isNull(), anyString());
    }

    @Test
    @DisplayName("A6 未选择复查结论：提示选择结论")
    void missingActionRejected() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));

        mockMvc.perform(post("/workorders/{id}/review", TestFixtures.WORK_ORDER_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("detailError", containsString("请选择复查结论")));

        verify(workOrderService, never()).review(anyLong(), anyBoolean(), any(), anyString());
    }

    @Test
    @DisplayName("A6 复查意见超过 300 字：拒绝")
    void tooLongCommentRejected() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));

        mockMvc.perform(post("/workorders/{id}/review", TestFixtures.WORK_ORDER_ID)
                        .param("action", "RETURN")
                        .param("reviewComment", "长".repeat(301)))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("detailError", containsString("不能超过 300 字")));

        verify(workOrderService, never()).review(anyLong(), anyBoolean(), any(), anyString());
    }

    @Test
    @DisplayName("A6 服务层拒绝非法状态：异常文案带回详情页")
    void serviceRejectionIsFlashed() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));
        willThrow(new WorkflowException(
                WorkflowException.Code.ILLEGAL_TRANSITION,
                "当前状态（待复查）不允许该操作"))
                .given(workOrderService).review(eq(TestFixtures.WORK_ORDER_ID), eq(true), any(), anyString());

        mockMvc.perform(post("/workorders/{id}/review", TestFixtures.WORK_ORDER_ID)
                        .param("action", "PASS"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("detailError", containsString("不允许该操作")));
    }
}
