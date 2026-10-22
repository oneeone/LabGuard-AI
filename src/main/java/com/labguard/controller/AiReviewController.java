package com.labguard.controller;

import com.labguard.model.AiReview;
import com.labguard.service.AiVisionException;
import com.labguard.service.RemediationReviewService;
import com.labguard.workflow.WorkflowException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 整改照片 AI 核验入口。只负责触发核验与回显提示，
 * 不接收结论参数，也不改变工单状态。
 */
@Controller
public class AiReviewController {

    private final RemediationReviewService reviewService;

    public AiReviewController(RemediationReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/workorders/{id}/ai-review")
    public String run(@PathVariable("id") long workOrderId, RedirectAttributes redirectAttributes) {
        try {
            AiReview review = reviewService.review(workOrderId);
            redirectAttributes.addFlashAttribute("detailMessage",
                    "AI 核验完成：" + review.outcome().label() + "（需人工确认）");
        } catch (AiVisionException ex) {
            redirectAttributes.addFlashAttribute("detailError", "AI 核验失败：" + ex.getMessage());
        } catch (WorkflowException | IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("detailError", ex.getMessage());
        }
        return "redirect:/workorders/" + workOrderId;
    }
}
