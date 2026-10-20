package com.labguard.web.form;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * F3 工单创建表单。隐患编号来自隐藏域，由服务层再次校验"已确认且未建单"。
 */
public class WorkOrderForm {

    @NotNull(message = "请选择需要创建工单的隐患")
    private Long hazardId;

    @NotBlank(message = "请填写责任人")
    @Size(max = 30, message = "责任人姓名不能超过 30 个字符")
    private String assignee;

    @NotNull(message = "请选择截止日期")
    @FutureOrPresent(message = "截止日期不能早于今天")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dueDate;

    @Size(max = 200, message = "备注不能超过 200 字")
    private String note;

    public Long getHazardId() {
        return hazardId;
    }

    public void setHazardId(Long hazardId) {
        this.hazardId = hazardId;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
