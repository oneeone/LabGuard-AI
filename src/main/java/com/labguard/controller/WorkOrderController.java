package com.labguard.controller;

import com.labguard.model.Hazard;
import com.labguard.model.HazardStatus;
import com.labguard.model.WorkOrderDetail;
import com.labguard.model.WorkOrderStatus;
import com.labguard.service.HazardService;
import com.labguard.service.LocalFileStorage;
import com.labguard.service.RemediationReviewService;
import com.labguard.service.WorkOrderService;
import com.labguard.service.WorkOrderTimelineService;
import com.labguard.web.ImageUploadValidator;
import com.labguard.web.WebFormat;
import com.labguard.web.form.WorkOrderForm;
import com.labguard.web.view.HazardCard;
import com.labguard.web.view.WorkOrderRow;
import com.labguard.workflow.WorkflowException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * R6-R11：工单列表、创建、详情、整改提交与复查。
 * 页面按状态显隐按钮，真正的合法性仍由 WorkOrderService 校验。
 */
@Controller
public class WorkOrderController {

    private static final String OPERATOR = "巡检员";
    private static final String REVIEWER = "复查员";
    private static final int MAX_REMEDIATION_NOTE = 500;
    private static final int MAX_REVIEW_COMMENT = 300;

    private final WorkOrderService workOrderService;
    private final HazardService hazardService;
    private final LocalFileStorage fileStorage;
    private final WorkOrderTimelineService timelineService;
    private final RemediationReviewService remediationReviewService;

    public WorkOrderController(WorkOrderService workOrderService,
                               HazardService hazardService,
                               LocalFileStorage fileStorage,
                               WorkOrderTimelineService timelineService,
                               RemediationReviewService remediationReviewService) {
        this.workOrderService = workOrderService;
        this.hazardService = hazardService;
        this.fileStorage = fileStorage;
        this.timelineService = timelineService;
        this.remediationReviewService = remediationReviewService;
    }

    /** R6 工单列表，status 为空表示全部。 */
    @GetMapping("/workorders")
    public String list(@RequestParam(value = "status", required = false) String status, Model model) {
        WorkOrderStatus filter = parseStatus(status);
        List<WorkOrderRow> rows = workOrderService.list(filter).stream()
                .map(WorkOrderRow::of)
                .toList();
        model.addAttribute("orders", rows);
        model.addAttribute("statusFilter", filter == null ? "" : filter.name());
        model.addAttribute("statuses", WorkOrderStatus.values());
        if (status != null && !status.isBlank() && filter == null) {
            model.addAttribute("listError", "未知的工单状态筛选条件");
        }
        return "workorders";
    }

    /** R7 创建工单表单，只允许已确认且尚未建单的隐患进入。 */
    @GetMapping("/workorders/new")
    public String createForm(@RequestParam("hazardId") long hazardId,
                             Model model,
                             RedirectAttributes redirect) {
        Hazard hazard;
        try {
            hazard = hazardService.get(hazardId);
        } catch (WorkflowException ex) {
            redirect.addFlashAttribute("listError", ex.getMessage());
            return "redirect:/workorders";
        }
        if (hazard.status() != HazardStatus.CONFIRMED) {
            redirect.addFlashAttribute("reviewError", "风险未确认，不能创建工单");
            return "redirect:/inspections/" + hazard.inspectionId() + "/analysis";
        }
        if (workOrderService.existsByHazard(hazardId)) {
            redirect.addFlashAttribute("reviewError", "该隐患已存在整改工单，不能重复创建");
            return "redirect:/inspections/" + hazard.inspectionId() + "/analysis";
        }

        WorkOrderForm form = new WorkOrderForm();
        form.setHazardId(hazardId);
        model.addAttribute("form", form);
        model.addAttribute("hazard", HazardCard.of(hazard, false));
        return "workorder-form";
    }

    /** R8 创建工单。 */
    @PostMapping("/workorders")
    public String create(@Valid @ModelAttribute("form") WorkOrderForm form,
                         BindingResult binding,
                         Model model,
                         RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            addHazardCard(form.getHazardId(), model);
            return "workorder-form";
        }
        try {
            long workOrderId = workOrderService.create(
                    form.getHazardId(),
                    form.getAssignee().trim(),
                    form.getDueDate(),
                    blankToNull(form.getNote()),
                    OPERATOR);
            redirect.addFlashAttribute("detailMessage", "整改工单已创建，请按截止日期完成整改。");
            return "redirect:/workorders/" + workOrderId;
        } catch (WorkflowException ex) {
            model.addAttribute("formError", ex.getMessage());
            addHazardCard(form.getHazardId(), model);
            return "workorder-form";
        }
    }

    /** R9 工单详情。 */
    @GetMapping("/workorders/{id}")
    public String detail(@PathVariable("id") long workOrderId, Model model) {
        WorkOrderDetail detail = requireDetail(workOrderId);
        WorkOrderStatus status = detail.order().status();

        model.addAttribute("detail", detail);
        model.addAttribute("row", WorkOrderRow.of(detail));
        model.addAttribute("order", detail.order());
        model.addAttribute("inspection", detail.inspection());
        model.addAttribute("hazard", HazardCard.of(detail.hazard(), true));
        model.addAttribute("statusLabel", status == null ? "" : status.label());
        model.addAttribute("statusClass", WorkOrderRow.statusCss(status));
        model.addAttribute("canRemediate", status == WorkOrderStatus.PENDING_REMEDIATION);
        model.addAttribute("canReview", status == WorkOrderStatus.PENDING_REVIEW);
        model.addAttribute("closed", status == WorkOrderStatus.CLOSED);
        model.addAttribute("dueDateText", WebFormat.date(detail.order().dueDate()));
        model.addAttribute("createdAtText", WebFormat.dateTime(detail.order().createdAt()));
        model.addAttribute("updatedAtText", WebFormat.dateTime(detail.order().updatedAt()));
        model.addAttribute("reviewedAtText", WebFormat.dateTime(detail.order().reviewedAt()));
        model.addAttribute("reviewResultLabel", reviewResultLabel(status, detail.order().reviewedAt()));
        model.addAttribute("inspectedAtText", WebFormat.dateTime(detail.inspection().inspectedAt()));
        model.addAttribute("timeline", timelineService.entriesFor(workOrderId));
        model.addAttribute("aiReview", remediationReviewService.latestFor(workOrderId));
        model.addAttribute("maxRemediationNote", MAX_REMEDIATION_NOTE);
        model.addAttribute("maxReviewComment", MAX_REVIEW_COMMENT);
        return "workorder-detail";
    }

    /** R10 提交整改说明与整改后照片。 */
    @PostMapping("/workorders/{id}/remediation")
    public String submitRemediation(@PathVariable("id") long workOrderId,
                                    @RequestParam(value = "remediationNote", required = false) String remediationNote,
                                    @RequestParam(value = "afterImage", required = false) MultipartFile afterImage,
                                    RedirectAttributes redirect) {
        WorkOrderDetail detail = findDetail(workOrderId);
        if (detail == null) {
            redirect.addFlashAttribute("detailError", "工单不存在或已被删除");
            return "redirect:/workorders";
        }
        if (detail.order().status() != WorkOrderStatus.PENDING_REMEDIATION) {
            redirect.addFlashAttribute("detailError", illegalStatusMessage(detail.order().status()));
            return "redirect:/workorders/" + workOrderId;
        }
        if (remediationNote == null || remediationNote.isBlank()) {
            redirect.addFlashAttribute("detailError", "请填写整改说明");
            return "redirect:/workorders/" + workOrderId;
        }
        if (remediationNote.trim().length() > MAX_REMEDIATION_NOTE) {
            redirect.addFlashAttribute("detailError", "整改说明不能超过 500 字");
            return "redirect:/workorders/" + workOrderId;
        }
        String fileError = ImageUploadValidator.validate(afterImage);
        if (fileError != null) {
            redirect.addFlashAttribute("detailError", "整改照片：" + fileError);
            return "redirect:/workorders/" + workOrderId;
        }

        try {
            String afterImagePath = fileStorage.store(afterImage.getBytes(), afterImage.getOriginalFilename());
            workOrderService.submitRemediation(workOrderId, remediationNote.trim(), afterImagePath,
                    detail.order().assignee());
            redirect.addFlashAttribute("detailMessage", "整改已提交，等待复查。");
        } catch (IOException ex) {
            redirect.addFlashAttribute("detailError", "整改照片读取失败，请重新上传");
        } catch (IllegalStateException ex) {
            redirect.addFlashAttribute("detailError", ex.getMessage());
        } catch (WorkflowException ex) {
            redirect.addFlashAttribute("detailError", ex.getMessage());
        }
        return "redirect:/workorders/" + workOrderId;
    }

    /** R11 复查通过或退回。 */
    @PostMapping("/workorders/{id}/review")
    public String review(@PathVariable("id") long workOrderId,
                         @RequestParam(value = "action", required = false) String action,
                         @RequestParam(value = "reviewComment", required = false) String reviewComment,
                         RedirectAttributes redirect) {
        WorkOrderDetail detail = findDetail(workOrderId);
        if (detail == null) {
            redirect.addFlashAttribute("detailError", "工单不存在或已被删除");
            return "redirect:/workorders";
        }
        if (detail.order().status() != WorkOrderStatus.PENDING_REVIEW) {
            redirect.addFlashAttribute("detailError", illegalStatusMessage(detail.order().status()));
            return "redirect:/workorders/" + workOrderId;
        }

        boolean passed;
        if ("PASS".equalsIgnoreCase(action)) {
            passed = true;
        } else if ("RETURN".equalsIgnoreCase(action)) {
            passed = false;
        } else {
            redirect.addFlashAttribute("detailError", "请选择复查结论");
            return "redirect:/workorders/" + workOrderId;
        }
        if (!passed && (reviewComment == null || reviewComment.isBlank())) {
            redirect.addFlashAttribute("detailError", "退回整改时必须填写复查意见");
            return "redirect:/workorders/" + workOrderId;
        }
        if (reviewComment != null && reviewComment.trim().length() > MAX_REVIEW_COMMENT) {
            redirect.addFlashAttribute("detailError", "复查意见不能超过 300 字");
            return "redirect:/workorders/" + workOrderId;
        }

        try {
            workOrderService.review(workOrderId, passed, blankToNull(reviewComment), REVIEWER);
            redirect.addFlashAttribute("detailMessage",
                    passed ? "复查通过，工单已关闭。" : "复查未通过，已退回整改。");
        } catch (WorkflowException ex) {
            redirect.addFlashAttribute("detailError", ex.getMessage());
        }
        return "redirect:/workorders/" + workOrderId;
    }

    private WorkOrderDetail requireDetail(long workOrderId) {
        try {
            return workOrderService.detail(workOrderId);
        } catch (WorkflowException ex) {
            if (ex.code() == WorkflowException.Code.WORK_ORDER_NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
            }
            throw ex;
        }
    }

    private WorkOrderDetail findDetail(long workOrderId) {
        try {
            return workOrderService.detail(workOrderId);
        } catch (WorkflowException ex) {
            return null;
        }
    }

    private void addHazardCard(Long hazardId, Model model) {
        if (hazardId == null) {
            return;
        }
        try {
            model.addAttribute("hazard",
                    HazardCard.of(hazardService.get(hazardId), workOrderService.existsByHazard(hazardId)));
        } catch (WorkflowException ignored) {
            // 隐患已被删除时不阻断错误页渲染
        }
    }

    private static WorkOrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return WorkOrderStatus.valueOf(status.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** 已复查的工单展示结论；未复查时为空串。 */
    private static String reviewResultLabel(WorkOrderStatus status, LocalDateTime reviewedAt) {
        if (reviewedAt == null) {
            return "";
        }
        return status == WorkOrderStatus.CLOSED ? "通过" : "退回整改";
    }

    private static String illegalStatusMessage(WorkOrderStatus status) {
        return "当前状态（" + (status == null ? "未知" : status.label()) + "）不允许该操作";
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
