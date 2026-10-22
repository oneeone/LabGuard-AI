package com.labguard.controller;

import com.labguard.model.AiResultSource;
import com.labguard.model.AiReview;
import com.labguard.model.CheckStatus;
import com.labguard.model.Comparability;
import com.labguard.model.EvidenceCheck;
import com.labguard.model.ReviewOutcome;
import com.labguard.service.AiErrorType;
import com.labguard.service.AiVisionException;
import com.labguard.service.RemediationReviewService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 核验入口只负责触发与提示，不接收结论参数，也不改变工单状态。
 */
@WebMvcTest(controllers = AiReviewController.class)
class AiReviewControllerTest {

    private static final long ORDER_ID = 12L;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RemediationReviewService reviewService;

    @Test
    @DisplayName("A-22-06 核验成功：回到详情页并提示需人工确认")
    void successRedirectsWithMessage() throws Exception {
        given(reviewService.review(ORDER_ID)).willReturn(review());

        mockMvc.perform(post("/workorders/{id}/ai-review", ORDER_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/workorders/12"))
                .andExpect(flash().attribute("detailMessage", containsString("AI 核验完成")))
                .andExpect(flash().attribute("detailMessage", containsString("人工确认")));
    }

    @Test
    @DisplayName("A-22-07 AI 调用失败：不返回成功提示，回显错误")
    void aiFailureShowsError() throws Exception {
        willThrow(new AiVisionException(AiErrorType.TIMEOUT))
                .given(reviewService).review(anyLong());

        mockMvc.perform(post("/workorders/{id}/ai-review", ORDER_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("detailError", containsString("AI 核验失败")));
    }

    @Test
    @DisplayName("A-22-08 非法状态：回显业务异常文案")
    void workflowFailureShowsError() throws Exception {
        willThrow(new WorkflowException(WorkflowException.Code.ILLEGAL_TRANSITION,
                "当前状态（待整改）不允许 AI 核验")).given(reviewService).review(anyLong());

        mockMvc.perform(post("/workorders/{id}/ai-review", ORDER_ID))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("detailError", containsString("不允许 AI 核验")));
    }

    private AiReview review() {
        return new AiReview(1L, ORDER_ID, ReviewOutcome.INCONCLUSIVE, "证据不足",
                List.of(new EvidenceCheck("纸箱堆放在安全出口前", CheckStatus.INCONCLUSIVE, "", "角度遮挡")),
                Comparability.PARTIAL, "仅依据当前视角", "请重新拍摄", AiResultSource.REAL,
                "vision-test-model", null, true, LocalDateTime.of(2026, 10, 22, 20, 0));
    }
}
