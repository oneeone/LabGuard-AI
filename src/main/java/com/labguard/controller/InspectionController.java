package com.labguard.controller;

import com.labguard.service.AiErrorType;
import com.labguard.service.AiVisionException;
import com.labguard.service.InspectionDraft;
import com.labguard.service.InspectionService;
import com.labguard.web.ImageUploadValidator;
import com.labguard.web.form.InspectionForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * R2 / R3：上传页与提交。文件与字段校验都在这里做，
 * 视觉分析失败时不落库，直接回到表单并保留已填内容。
 */
@Controller
public class InspectionController {

    private final InspectionService inspectionService;

    public InspectionController(InspectionService inspectionService) {
        this.inspectionService = inspectionService;
    }

    @GetMapping("/inspections/new")
    public String form(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new InspectionForm());
        }
        return "inspection-form";
    }

    @PostMapping("/inspections")
    public String create(@Valid @ModelAttribute("form") InspectionForm form,
                         BindingResult binding,
                         @RequestParam(value = "file", required = false) MultipartFile file,
                         Model model) {
        String fileError = ImageUploadValidator.validate(file);
        if (fileError != null) {
            model.addAttribute("fileError", fileError);
        }
        if (binding.hasErrors() || fileError != null) {
            return "inspection-form";
        }

        byte[] imageBytes;
        try {
            imageBytes = file.getBytes();
        } catch (IOException ex) {
            model.addAttribute("fileError", "图片读取失败，请重新上传");
            return "inspection-form";
        }

        try {
            long inspectionId = inspectionService.create(new InspectionDraft(
                    form.getLabName().trim(),
                    form.getInspector().trim(),
                    form.getInspectionTime() == null ? LocalDateTime.now() : form.getInspectionTime(),
                    blankToNull(form.getNote()),
                    imageBytes,
                    file.getOriginalFilename()));
            return "redirect:/inspections/" + inspectionId + "/analysis";
        } catch (AiVisionException ex) {
            model.addAttribute("analysisError", analysisErrorMessage(ex));
            return "inspection-form";
        } catch (IllegalStateException ex) {
            model.addAttribute("analysisError", ex.getMessage());
            return "inspection-form";
        }
    }

    private static String analysisErrorMessage(AiVisionException ex) {
        if (ex.type() == AiErrorType.NOT_CONFIGURED) {
            return "视觉 API 未配置完整，请检查 VISION_MODE / VISION_API_KEY";
        }
        return "AI 分析失败：" + ex.type().name() + "。" + ex.type().defaultMessage();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
