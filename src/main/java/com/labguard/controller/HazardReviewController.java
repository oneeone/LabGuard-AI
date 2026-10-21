package com.labguard.controller;

import com.labguard.model.Hazard;
import com.labguard.service.HazardEditCommand;
import com.labguard.service.HazardService;
import com.labguard.web.form.HazardReviewForm;
import com.labguard.workflow.WorkflowException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * R5：对单条风险执行确认、修改或驳回。合法性以 HazardService 为准，
 * 页面只负责展示错误并回到分析结果页。
 */
@Controller
public class HazardReviewController {

    private static final String OPERATOR = "巡检员";

    private final HazardService hazardService;

    public HazardReviewController(HazardService hazardService) {
        this.hazardService = hazardService;
    }

    @PostMapping("/hazards/{id}/confirm")
    public String review(@PathVariable("id") long hazardId,
                         @ModelAttribute("reviewForm") HazardReviewForm form,
                         RedirectAttributes redirect) {
        Hazard hazard;
        try {
            hazard = hazardService.get(hazardId);
        } catch (WorkflowException ex) {
            redirect.addFlashAttribute("reviewError", ex.getMessage());
            return "redirect:/inspections/new";
        }

        String action = form.getAction() == null ? "" : form.getAction().trim().toUpperCase();
        try {
            switch (action) {
                case "CONFIRM" -> {
                    hazardService.confirm(hazardId, OPERATOR);
                    redirect.addFlashAttribute("reviewMessage", "风险已确认，可创建整改工单。");
                }
                case "EDIT" -> {
                    hazardService.edit(hazardId, new HazardEditCommand(
                            form.getCategory(), form.getRiskLevel(), form.getLocation(),
                            form.getEvidence(), form.getSuggestedAction()), OPERATOR);
                    redirect.addFlashAttribute("reviewMessage", "风险已修改并确认。");
                }
                case "REJECT" -> {
                    hazardService.reject(hazardId, form.getReviewNote(), OPERATOR);
                    redirect.addFlashAttribute("reviewMessage", "风险已驳回，可重新上传图片复检。");
                }
                default -> throw new WorkflowException(WorkflowException.Code.INVALID_ARGUMENT,
                        "请选择确认、修改或驳回");
            }
        } catch (WorkflowException ex) {
            redirect.addFlashAttribute("reviewError", ex.getMessage());
        }
        return "redirect:/inspections/" + hazard.inspectionId() + "/analysis";
    }
}
