package com.labguard.controller;

import com.labguard.model.AiResultSource;
import com.labguard.model.AiReview;
import com.labguard.model.AuditEvent;
import com.labguard.model.CheckStatus;
import com.labguard.model.Comparability;
import com.labguard.model.EvidenceCheck;
import com.labguard.model.ReviewOutcome;
import com.labguard.model.WorkOrderStatus;
import com.labguard.service.HazardService;
import com.labguard.service.LocalFileStorage;
import com.labguard.service.WorkOrderService;
import com.labguard.service.RemediationReviewService;
import com.labguard.service.WorkOrderTimelineService;
import com.labguard.web.view.TimelineEntry;
import com.labguard.workflow.WorkflowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

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

    @MockitoBean
    private WorkOrderTimelineService timelineService;

    @MockitoBean
    private RemediationReviewService remediationReviewService;

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
                .andExpect(content().string(not(containsString("退回整改"))))
                .andExpect(content().string(not(containsString("AI 前后对比核验"))));
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
    @DisplayName("附录 E 处理时间线：展示历史动作与当次整改说明")
    void timelineShowsHistoryAndRemediationNotes() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));
        AuditEvent submitEvent = AuditEvent.of(AuditEvent.ENTITY_WORK_ORDER, TestFixtures.WORK_ORDER_ID,
                "SUBMIT_REMEDIATION", "PENDING_REMEDIATION", "PENDING_REVIEW", "李四",
                "提交整改", "{\"remediationNote\":\"第一次整改后又出现堆放\"}");
        given(timelineService.entriesFor(TestFixtures.WORK_ORDER_ID))
                .willReturn(List.of(TimelineEntry.of(submitEvent, "第一次整改后又出现堆放")));

        mockMvc.perform(get("/workorders/{id}", TestFixtures.WORK_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("处理时间线")))
                .andExpect(content().string(containsString("待整改 → 待复查")))
                .andExpect(content().string(containsString("第一次整改后又出现堆放")));
    }

    @Test
    @DisplayName("附录 E 无历史记录时不渲染时间线区块")
    void emptyTimelineHidesSection() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REMEDIATION));

        mockMvc.perform(get("/workorders/{id}", TestFixtures.WORK_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("处理时间线"))));
    }

    @Test
    @DisplayName("A-22-11 待复查无核验记录：展示 AI 核验入口按钮")
    void pendingReviewShowsAiReviewEntry() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));

        mockMvc.perform(get("/workorders/{id}", TestFixtures.WORK_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI 前后对比核验")))
                .andExpect(content().string(containsString("尚未执行 AI 核验")))
                .andExpect(content().string(containsString("对比两张照片并生成核验清单")));
    }

    @Test
    @DisplayName("A-22-12 有核验记录：逐点清单、可比性与人工确认徽标正常渲染")
    void aiReviewPanelRendersChecks() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));
        given(remediationReviewService.latestFor(TestFixtures.WORK_ORDER_ID)).willReturn(new AiReview(
                1L, TestFixtures.WORK_ORDER_ID, ReviewOutcome.PASSED, "出口前地面空置",
                List.of(new EvidenceCheck("纸箱堆放在安全出口前", CheckStatus.RESOLVED,
                        "出口左侧无纸箱", "")),
                Comparability.COMPARABLE, "仅依据当前视角", "请人工确认后关闭", AiResultSource.REAL,
                "vision-test-model", null, true, LocalDateTime.of(2026, 10, 22, 20, 0)));

        mockMvc.perform(get("/workorders/{id}", TestFixtures.WORK_ORDER_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AI 前后对比核验")))
                .andExpect(content().string(containsString("原始证据核验点")))
                .andExpect(content().string(containsString("纸箱堆放在安全出口前")))
                .andExpect(content().string(containsString("已解决")))
                .andExpect(content().string(containsString("视角可比")))
                .andExpect(content().string(containsString("真实 API")))
                .andExpect(content().string(containsString("需人工确认")))
                .andExpect(content().string(containsString("2026-10-22 20:00")));
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
