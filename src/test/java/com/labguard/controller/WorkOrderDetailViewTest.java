package com.labguard.controller;

import com.labguard.model.WorkOrderStatus;
import com.labguard.service.HazardService;
import com.labguard.service.LocalFileStorage;
import com.labguard.service.WorkOrderService;
import com.labguard.workflow.WorkflowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 附录 A7：工单详情页按状态显隐操作按钮（按钮显隐矩阵 5.2）。
 */
@WebMvcTest(controllers = WorkOrderController.class)
class WorkOrderDetailViewTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WorkOrderService workOrderService;

    @MockitoBean
    private HazardService hazardService;

    @MockitoBean
    private LocalFileStorage fileStorage;

    @Test
    @DisplayName("A7 待整改详情页：只有提交整改")
    void pendingRemediationShowsSubmitOnly() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REMEDIATION));

        mockMvc.perform(get("/workorders/{id}", TestFixtures.WORK_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("workorder-detail"))
                .andExpect(content().string(containsString("提交整改")))
                .andExpect(content().string(containsString("尚未提交整改证据")))
                .andExpect(content().string(not(containsString("复查通过"))))
                .andExpect(content().string(not(containsString("退回整改"))));
    }

    @Test
    @DisplayName("A7 待复查详情页：包含复查通过与退回整改，不含提交整改")
    void pendingReviewShowsReviewActionsOnly() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));

        mockMvc.perform(get("/workorders/{id}", TestFixtures.WORK_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("复查通过")))
                .andExpect(content().string(containsString("退回整改")))
                .andExpect(content().string(not(containsString("尚未提交整改证据"))));
    }

    @Test
    @DisplayName("A7 已关闭详情页：三个操作按钮都不出现")
    void closedShowsNoActions() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.CLOSED));

        mockMvc.perform(get("/workorders/{id}", TestFixtures.WORK_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("工单已关闭")))
                .andExpect(content().string(containsString("复查合格")))
                .andExpect(content().string(not(containsString("提交整改"))))
                .andExpect(content().string(not(containsString("复查通过"))))
                .andExpect(content().string(not(containsString("退回整改"))));
    }

    @Test
    @DisplayName("S3 详情页展示基本信息与整改前后对比")
    void detailShowsBasicInfoAndComparison() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));

        mockMvc.perform(get("/workorders/{id}", TestFixtures.WORK_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("整改前后对比")))
                .andExpect(content().string(containsString("化学实验楼 A302")))
                .andExpect(content().string(containsString("李四")))
                .andExpect(content().string(containsString("/uploads/original.jpg")))
                .andExpect(content().string(containsString("/uploads/after.jpg")))
                .andExpect(content().string(containsString("已清理纸箱")));
    }

    @Test
    @DisplayName("R9 工单不存在：返回 404")
    void unknownWorkOrderReturns404() throws Exception {
        given(workOrderService.detail(404L))
                .willThrow(new WorkflowException(
                        WorkflowException.Code.WORK_ORDER_NOT_FOUND, "工单不存在或已被删除"));

        mockMvc.perform(get("/workorders/{id}", 404L))
                .andExpect(status().isNotFound());
    }
}
