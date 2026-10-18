package com.labguard.controller;

import com.labguard.model.AiAnalysisResult;
import com.labguard.model.Hazard;
import com.labguard.service.AiVisionException;
import com.labguard.service.AiVisionService;
import com.labguard.service.ImageInput;
import com.labguard.service.WorkOrderService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
public class HomeController {
    private final AiVisionService aiVisionService;
    private final WorkOrderService workOrderService;

    public HomeController(AiVisionService aiVisionService, WorkOrderService workOrderService) {
        this.aiVisionService = aiVisionService;
        this.workOrderService = workOrderService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("workOrders", workOrderService.findAll());
        return "index";
    }

    @PostMapping("/inspect")
    public String inspect(@RequestParam("image") MultipartFile image, Model model) throws IOException {
        if (image.isEmpty()) {
            model.addAttribute("error", "请先选择一张图片。");
            model.addAttribute("workOrders", workOrderService.findAll());
            return "index";
        }
        try {
            AiAnalysisResult analysis = aiVisionService.analyze(
                    ImageInput.of(image.getBytes(), image.getOriginalFilename()));
            model.addAttribute("analysis", analysis);
            model.addAttribute("hazards", analysis.hazards());
            model.addAttribute("filename", image.getOriginalFilename());
        } catch (AiVisionException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("errorType", ex.type().name());
        } catch (RuntimeException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        model.addAttribute("workOrders", workOrderService.findAll());
        return "index";
    }

    @PostMapping("/work-orders")
    public String createWorkOrders(
            @RequestParam String category,
            @RequestParam String riskLevel,
            @RequestParam String location,
            @RequestParam String evidence,
            @RequestParam(required = false) Double confidence,
            @RequestParam(defaultValue = "") String uncertainty,
            @RequestParam String suggestedAction,
            @RequestParam(defaultValue = "true") boolean needsHumanReview,
            @RequestParam(defaultValue = "false") boolean demoData,
            @RequestParam(defaultValue = "待分配") String owner,
            RedirectAttributes redirect) {
        Hazard hazard = new Hazard(category, riskLevel, location, evidence, confidence, uncertainty,
                suggestedAction, needsHumanReview, demoData);
        workOrderService.create(hazard, owner);
        redirect.addFlashAttribute("message", "整改工单已创建。");
        return "redirect:/";
    }

    @PostMapping("/work-orders/{id}/rectification")
    public String rectification(@PathVariable long id, @RequestParam String note, RedirectAttributes redirect) {
        try {
            workOrderService.submitRectification(id, note);
            redirect.addFlashAttribute("message", "整改已提交，等待复查。");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }

    @PostMapping("/work-orders/{id}/review")
    public String review(@PathVariable long id, @RequestParam boolean passed,
                         @RequestParam(required = false) String note, RedirectAttributes redirect) {
        try {
            workOrderService.review(id, passed, note);
            redirect.addFlashAttribute("message", passed ? "复查通过，工单已关闭。" : "复查未通过，已退回整改。");
        } catch (RuntimeException ex) {
            redirect.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/";
    }
}
