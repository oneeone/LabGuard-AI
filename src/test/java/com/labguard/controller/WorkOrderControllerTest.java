package com.labguard.controller;

import com.labguard.model.HazardStatus;
import com.labguard.model.WorkOrderStatus;
import com.labguard.service.HazardService;
import com.labguard.service.LocalFileStorage;
import com.labguard.service.WorkOrderService;
import com.labguard.service.RemediationReviewService;
import com.labguard.service.WorkOrderTimelineService;
import com.labguard.workflow.WorkflowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 附录 A4：创建工单；同时覆盖 R6 列表筛选与 R7 建单入口的前置校验。
 */
@WebMvcTest(controllers = WorkOrderController.class)
class WorkOrderControllerTest {

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

    @MockitoBean
    private RemediationReviewService remediationReviewService;

    @Test
    @DisplayName("A4 正常创建：重定向到详情页并闪现成功提示")
    void createRedirectsToDetail() throws Exception {
        LocalDate dueDate = LocalDate.now().plusDays(3);
        given(workOrderService.create(eq(TestFixtures.HAZARD_ID), eq("李四"), eq(dueDate), isNull(), anyString()))
                .willReturn(TestFixtures.WORK_ORDER_ID);

        mockMvc.perform(post("/workorders")
                        .param("hazardId", String.valueOf(TestFixtures.HAZARD_ID))
                        .param("assignee", "李四")
                        .param("dueDate", dueDate.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/workorders/12"))
                .andExpect(flash().attribute("detailMessage", containsString("整改工单已创建")));

        verify(workOrderService).create(TestFixtures.HAZARD_ID, "李四", dueDate, null, "巡检员");
    }

    @Test
    @DisplayName("A4 hazardId 未确认：拒绝并提示风险未确认")
    void createWithUnconfirmedHazardRejected() throws Exception {
        given(workOrderService.create(anyLong(), anyString(), any(LocalDate.class),
                nullable(String.class), anyString()))
                .willThrow(new WorkflowException(WorkflowException.Code.HAZARD_NOT_CONFIRMED,
                        "风险未确认，不能创建工单"));
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));

        mockMvc.perform(post("/workorders")
                        .param("hazardId", String.valueOf(TestFixtures.HAZARD_ID))
                        .param("assignee", "李四")
                        .param("dueDate", LocalDate.now().plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("workorder-form"))
                .andExpect(model().attribute("formError", containsString("风险未确认，不能创建工单")))
                .andExpect(content().string(containsString("风险未确认，不能创建工单")));
    }

    @Test
    @DisplayName("A4 dueDate 早于今天：字段级错误且不调用服务")
    void pastDueDateIsFieldError() throws Exception {
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));

        mockMvc.perform(post("/workorders")
                        .param("hazardId", String.valueOf(TestFixtures.HAZARD_ID))
                        .param("assignee", "李四")
                        .param("dueDate", LocalDate.now().minusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("workorder-form"))
                .andExpect(model().attributeHasFieldErrors("form", "dueDate"))
                .andExpect(content().string(containsString("截止日期不能早于今天")));

        verify(workOrderService, never()).create(anyLong(), anyString(), any(LocalDate.class),
                nullable(String.class), anyString());
    }

    @Test
    @DisplayName("A4 assignee 为空：字段级错误")
    void blankAssigneeIsFieldError() throws Exception {
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));

        mockMvc.perform(post("/workorders")
                        .param("hazardId", String.valueOf(TestFixtures.HAZARD_ID))
                        .param("assignee", "")
                        .param("dueDate", LocalDate.now().plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "assignee"))
                .andExpect(content().string(containsString("请填写责任人")));

        verify(workOrderService, never()).create(anyLong(), anyString(), any(LocalDate.class),
                nullable(String.class), anyString());
    }

    @Test
    @DisplayName("R7 隐患未确认：不能进入建单表单")
    void createFormRequiresConfirmedHazard() throws Exception {
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.PENDING_CONFIRM));

        mockMvc.perform(get("/workorders/new").param("hazardId", String.valueOf(TestFixtures.HAZARD_ID)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inspections/7/analysis"))
                .andExpect(flash().attribute("reviewError", containsString("风险未确认，不能创建工单")));
    }

    @Test
    @DisplayName("R7 已存在工单：不能重复建单")
    void createFormRejectsDuplicateOrder() throws Exception {
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.CONFIRMED));
        given(workOrderService.existsByHazard(TestFixtures.HAZARD_ID)).willReturn(true);

        mockMvc.perform(get("/workorders/new").param("hazardId", String.valueOf(TestFixtures.HAZARD_ID)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inspections/7/analysis"))
                .andExpect(flash().attribute("reviewError", containsString("已存在整改工单")));
    }

    @Test
    @DisplayName("R7 已确认且未建单：进入建单表单并展示隐患摘要")
    void createFormShowsHazardSummary() throws Exception {
        given(hazardService.get(TestFixtures.HAZARD_ID))
                .willReturn(TestFixtures.hazard(HazardStatus.CONFIRMED));

        mockMvc.perform(get("/workorders/new").param("hazardId", String.valueOf(TestFixtures.HAZARD_ID)))
                .andExpect(status().isOk())
                .andExpect(view().name("workorder-form"))
                .andExpect(model().attributeExists("form", "hazard"))
                .andExpect(content().string(containsString("通道被遮挡")))
                .andExpect(content().string(containsString("创建整改工单")));
    }

    @Test
    @DisplayName("R6 工单列表：展示工单编号、类别与状态徽章")
    void listShowsWorkOrders() throws Exception {
        given(workOrderService.list(null))
                .willReturn(List.of(TestFixtures.detail(WorkOrderStatus.PENDING_REMEDIATION)));

        mockMvc.perform(get("/workorders"))
                .andExpect(status().isOk())
                .andExpect(view().name("workorders"))
                .andExpect(content().string(containsString("#12")))
                .andExpect(content().string(containsString("通道被遮挡")))
                .andExpect(content().string(containsString("待整改")))
                .andExpect(content().string(containsString("李四")));
    }

    @Test
    @DisplayName("R6 状态筛选：按 status 传给服务")
    void listFiltersByStatus() throws Exception {
        given(workOrderService.list(WorkOrderStatus.PENDING_REVIEW))
                .willReturn(List.of(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW)));

        mockMvc.perform(get("/workorders").param("status", "PENDING_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("待复查")));

        verify(workOrderService).list(WorkOrderStatus.PENDING_REVIEW);
    }

    @Test
    @DisplayName("R6 未知状态筛选：提示并展示全部")
    void listWithUnknownStatusShowsError() throws Exception {
        given(workOrderService.list(null)).willReturn(List.of());

        mockMvc.perform(get("/workorders").param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("listError", containsString("未知的工单状态")))
                .andExpect(content().string(containsString("当前筛选条件下没有工单")));
    }
}
