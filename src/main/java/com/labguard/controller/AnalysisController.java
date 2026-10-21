package com.labguard.controller;

import com.labguard.model.AiResultSource;
import com.labguard.model.Inspection;
import com.labguard.service.InspectionAnalysis;
import com.labguard.service.InspectionService;
import com.labguard.service.WorkOrderService;
import com.labguard.web.WebFormat;
import com.labguard.web.view.HazardCard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * R4：分析结果页。来源标识、置信度档位与人工核验提示都在
 * {@link HazardCard} 中算好，模板只负责输出。
 */
@Controller
public class AnalysisController {

    private final InspectionService inspectionService;
    private final WorkOrderService workOrderService;

    public AnalysisController(InspectionService inspectionService, WorkOrderService workOrderService) {
        this.inspectionService = inspectionService;
        this.workOrderService = workOrderService;
    }

    @GetMapping("/inspections/{id}/analysis")
    public String analysis(@PathVariable("id") long inspectionId, Model model) {
        InspectionAnalysis analysis = inspectionService.find(inspectionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "巡检记录不存在"));

        Inspection inspection = analysis.inspection();
        List<HazardCard> hazards = analysis.hazards().stream()
                .map(hazard -> HazardCard.of(hazard, workOrderService.existsByHazard(hazard.id())))
                .toList();
        boolean demo = inspection.analysisSource() == AiResultSource.DEMO;

        model.addAttribute("inspection", inspection);
        model.addAttribute("hazards", hazards);
        model.addAttribute("demo", demo);
        model.addAttribute("sourceLabel", demo ? "演示数据，非真实识别结果" : "真实 AI 分析");
        model.addAttribute("inspectedAtText", WebFormat.dateTime(inspection.inspectedAt()));
        model.addAttribute("pendingCount", hazards.stream().filter(HazardCard::pending).count());
        model.addAttribute("confirmedCount", hazards.stream().filter(HazardCard::confirmed).count());
        model.addAttribute("rejectedCount", hazards.stream().filter(HazardCard::rejected).count());
        return "analysis";
    }
}
