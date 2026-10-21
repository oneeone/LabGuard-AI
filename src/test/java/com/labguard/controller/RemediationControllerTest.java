package com.labguard.controller;

import com.labguard.model.WorkOrderStatus;
import com.labguard.service.HazardService;
import com.labguard.service.LocalFileStorage;
import com.labguard.service.WorkOrderService;
import com.labguard.service.WorkOrderTimelineService;
import com.labguard.workflow.WorkflowException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 附录 A5：整改提交。状态与必填项都在控制器里先行校验，
 * 非法请求不会触达文件落盘与状态流转。
 */
@WebMvcTest(controllers = WorkOrderController.class)
class RemediationControllerTest {

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

    @Test
    @DisplayName("A5 待整改状态提交：重定向到详情页并进入待复查")
    void submitRemediationSucceeds() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REMEDIATION));
        given(fileStorage.store(any(byte[].class), eq("after.jpg"))).willReturn("/uploads/after.jpg");

        mockMvc.perform(multipart("/workorders/{id}/remediation", TestFixtures.WORK_ORDER_ID)
                        .file(afterImage())
                        .param("remediationNote", "已清理纸箱"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/workorders/12"))
                .andExpect(flash().attribute("detailMessage", containsString("等待复查")));

        verify(workOrderService).submitRemediation(TestFixtures.WORK_ORDER_ID,
                "已清理纸箱", "/uploads/after.jpg", "李四");
    }

    @Test
    @DisplayName("A5 待复查状态重复提交：拒绝且提示当前状态")
    void submitWhenPendingReviewRejected() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REVIEW));

        mockMvc.perform(multipart("/workorders/{id}/remediation", TestFixtures.WORK_ORDER_ID)
                        .file(afterImage())
                        .param("remediationNote", "再次整改"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/workorders/12"))
                .andExpect(flash().attribute("detailError", containsString("当前状态（待复查）不允许该操作")));

        verifyNoInteractions(fileStorage);
        verify(workOrderService, never()).submitRemediation(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("A5 无整改照片：提示请选择图片，不落盘")
    void missingAfterImageRejected() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REMEDIATION));

        mockMvc.perform(multipart("/workorders/{id}/remediation", TestFixtures.WORK_ORDER_ID)
                        .param("remediationNote", "已清理纸箱"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("detailError", containsString("请选择图片")));

        verifyNoInteractions(fileStorage);
        verify(workOrderService, never()).submitRemediation(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("A5 无整改说明：提示请填写整改说明")
    void missingNoteRejected() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REMEDIATION));

        mockMvc.perform(multipart("/workorders/{id}/remediation", TestFixtures.WORK_ORDER_ID)
                        .file(afterImage()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("detailError", containsString("请填写整改说明")));

        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("A5 整改照片为 PDF：提示仅支持 JPG / PNG")
    void nonImageAfterFileRejected() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REMEDIATION));

        mockMvc.perform(multipart("/workorders/{id}/remediation", TestFixtures.WORK_ORDER_ID)
                        .file(new MockMultipartFile("afterImage", "report.pdf", "application/pdf", "pdf".getBytes()))
                        .param("remediationNote", "已清理纸箱"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("detailError", containsString("仅支持 JPG 或 PNG")));

        verifyNoInteractions(fileStorage);
    }

    @Test
    @DisplayName("A5 文件落盘失败：提示重新上传，不改变状态")
    void storageFailureShowsError() throws Exception {
        given(workOrderService.detail(TestFixtures.WORK_ORDER_ID))
                .willReturn(TestFixtures.detail(WorkOrderStatus.PENDING_REMEDIATION));
        given(fileStorage.store(any(byte[].class), anyString()))
                .willThrow(new IllegalStateException("图片保存失败，请重试"));

        mockMvc.perform(multipart("/workorders/{id}/remediation", TestFixtures.WORK_ORDER_ID)
                        .file(afterImage())
                        .param("remediationNote", "已清理纸箱"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("detailError", containsString("图片保存失败")));

        verify(workOrderService, never()).submitRemediation(anyLong(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("A5 工单不存在：回列表并提示")
    void unknownWorkOrderReturnsToList() throws Exception {
        given(workOrderService.detail(404L))
                .willThrow(new WorkflowException(
                        WorkflowException.Code.WORK_ORDER_NOT_FOUND, "工单不存在或已被删除"));

        mockMvc.perform(multipart("/workorders/{id}/remediation", 404L)
                        .file(afterImage())
                        .param("remediationNote", "已清理纸箱"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/workorders"))
                .andExpect(flash().attribute("detailError", containsString("工单不存在")));
    }

    private static MockMultipartFile afterImage() {
        return new MockMultipartFile("afterImage", "after.jpg", "image/jpeg", new byte[128]);
    }
}
