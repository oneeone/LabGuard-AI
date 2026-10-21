package com.labguard.web.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * F1 上传表单。图片由 MultipartFile 单独校验，其余字段在这里做字段级校验。
 */
public class InspectionForm {

    @NotBlank(message = "请填写实验室名称")
    @Size(max = 50, message = "实验室名称不能超过 50 个字符")
    private String labName;

    @NotBlank(message = "请填写巡检人姓名")
    @Size(max = 30, message = "巡检人姓名不能超过 30 个字符")
    private String inspector;

    @PastOrPresent(message = "巡检时间不能晚于当前时间")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime inspectionTime;

    @Size(max = 200, message = "备注不能超过 200 字")
    private String note;

    public String getLabName() {
        return labName;
    }

    public void setLabName(String labName) {
        this.labName = labName;
    }

    public String getInspector() {
        return inspector;
    }

    public void setInspector(String inspector) {
        this.inspector = inspector;
    }

    public LocalDateTime getInspectionTime() {
        return inspectionTime;
    }

    public void setInspectionTime(LocalDateTime inspectionTime) {
        this.inspectionTime = inspectionTime;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
