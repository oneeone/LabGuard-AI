package com.labguard.controller;

import com.labguard.service.AiErrorType;
import com.labguard.service.AiVisionException;
import com.labguard.service.InspectionDraft;
import com.labguard.service.InspectionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * 附录 A1：上传页与提交；并覆盖 A2 中属于 R3 的两个视觉 API 失败用例
 * （未配置、调用异常只可能发生在提交阶段，分析页无法产生这两种状态）。
 */
@WebMvcTest(controllers = InspectionController.class)
class InspectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InspectionService inspectionService;

    @Test
    @DisplayName("R2 打开上传页")
    void showUploadForm() throws Exception {
        mockMvc.perform(get("/inspections/new"))
                .andExpect(status().isOk())
                .andExpect(view().name("inspection-form"))
                .andExpect(content().string(containsString("data-file-drop")))
                .andExpect(content().string(containsString("data-file-preview")))
                .andExpect(content().string(containsString("data-datetime-field")))
                .andExpect(content().string(containsString("年/月/日 --:--")))
                .andExpect(content().string(containsString("nav-action")))
                .andExpect(content().string(containsString("/images/logo.svg")))
                .andExpect(model().attributeExists("form"));
    }

    @Test
    @DisplayName("A1 上传合法 JPG：重定向到分析结果页")
    void uploadValidJpgRedirectsToAnalysis() throws Exception {
        given(inspectionService.create(any(InspectionDraft.class))).willReturn(42L);

        mockMvc.perform(multipart("/inspections")
                        .file(jpg(1024))
                        .param("labName", "化学实验楼 A302")
                        .param("inspector", "张三"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/inspections/42/analysis"));

        ArgumentCaptor<InspectionDraft> captor = ArgumentCaptor.forClass(InspectionDraft.class);
        verify(inspectionService).create(captor.capture());
        assertThat(captor.getValue().labName()).isEqualTo("化学实验楼 A302");
        assertThat(captor.getValue().inspector()).isEqualTo("张三");
        assertThat(captor.getValue().filename()).isEqualTo("scene.jpg");
    }

    @Test
    @DisplayName("A1 空文件：回表单并提示请选择图片")
    void emptyFileReturnsToForm() throws Exception {
        mockMvc.perform(multipart("/inspections")
                        .file(new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]))
                        .param("labName", "化学实验楼 A302")
                        .param("inspector", "张三"))
                .andExpect(status().isOk())
                .andExpect(view().name("inspection-form"))
                .andExpect(model().attribute("fileError", containsString("请选择图片")))
                .andExpect(content().string(containsString("请选择图片")));

        verifyNoInteractions(inspectionService);
    }

    @Test
    @DisplayName("A1 上传 .txt：提示仅支持 JPG / PNG")
    void textFileRejectedByType() throws Exception {
        mockMvc.perform(multipart("/inspections")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes()))
                        .param("labName", "化学实验楼 A302")
                        .param("inspector", "张三"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fileError", containsString("仅支持 JPG 或 PNG")))
                .andExpect(content().string(containsString("仅支持 JPG 或 PNG")));

        verifyNoInteractions(inspectionService);
    }

    @Test
    @DisplayName("A1 上传 .pdf：提示仅支持 JPG / PNG")
    void pdfRejectedByType() throws Exception {
        mockMvc.perform(multipart("/inspections")
                        .file(new MockMultipartFile("file", "report.pdf", "application/pdf", "pdf".getBytes()))
                        .param("labName", "化学实验楼 A302")
                        .param("inspector", "张三"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fileError", containsString("仅支持 JPG 或 PNG")));

        verifyNoInteractions(inspectionService);
    }

    @Test
    @DisplayName("A1 上传 6MB 图片：提示超过 5MB")
    void oversizedImageRejected() throws Exception {
        mockMvc.perform(multipart("/inspections")
                        .file(jpg(6L * 1024 * 1024))
                        .param("labName", "化学实验楼 A302")
                        .param("inspector", "张三"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("fileError", containsString("图片大小不能超过 5MB")))
                .andExpect(content().string(containsString("图片大小不能超过 5MB")));

        verifyNoInteractions(inspectionService);
    }

    @Test
    @DisplayName("A1 labName 为空：字段级错误并保留已填内容")
    void blankLabNameKeepsOtherValues() throws Exception {
        mockMvc.perform(multipart("/inspections")
                        .file(jpg(1024))
                        .param("labName", "")
                        .param("inspector", "张三"))
                .andExpect(status().isOk())
                .andExpect(view().name("inspection-form"))
                .andExpect(model().attributeHasFieldErrors("form", "labName"))
                .andExpect(content().string(containsString("请填写实验室名称")))
                .andExpect(content().string(containsString("value=\"张三\"")));

        verifyNoInteractions(inspectionService);
    }

    @Test
    @DisplayName("A1 inspector 为空：字段级错误")
    void blankInspectorRejected() throws Exception {
        mockMvc.perform(multipart("/inspections")
                        .file(jpg(1024))
                        .param("labName", "化学实验楼 A302")
                        .param("inspector", ""))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "inspector"))
                .andExpect(content().string(containsString("请填写巡检人姓名")));

        verifyNoInteractions(inspectionService);
    }

    @Test
    @DisplayName("A1 inspectionTime 为未来时间：字段级错误")
    void futureInspectionTimeRejected() throws Exception {
        mockMvc.perform(multipart("/inspections")
                        .file(jpg(1024))
                        .param("labName", "化学实验楼 A302")
                        .param("inspector", "张三")
                        .param("inspectionTime", "2099-01-01T10:00"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("form", "inspectionTime"))
                .andExpect(content().string(containsString("巡检时间不能晚于当前时间")));

        verifyNoInteractions(inspectionService);
    }

    @Test
    @DisplayName("A2 视觉 API 未配置：回上传页提示配置项，不渲染任何风险数据")
    void notConfiguredShowsConfigHint() throws Exception {
        given(inspectionService.create(any(InspectionDraft.class)))
                .willThrow(new AiVisionException(AiErrorType.NOT_CONFIGURED));

        mockMvc.perform(multipart("/inspections")
                        .file(jpg(1024))
                        .param("labName", "化学实验楼 A302")
                        .param("inspector", "张三"))
                .andExpect(status().isOk())
                .andExpect(view().name("inspection-form"))
                .andExpect(content().string(containsString("视觉 API 未配置完整")))
                .andExpect(content().string(not(containsString("风险线索"))))
                .andExpect(content().string(not(containsString("建议风险等级"))));
    }

    @Test
    @DisplayName("A2 视觉 API 抛异常：显示错误类别并提供重试入口")
    void apiFailureShowsErrorTypeAndRetry() throws Exception {
        given(inspectionService.create(any(InspectionDraft.class)))
                .willThrow(new AiVisionException(AiErrorType.TIMEOUT));

        mockMvc.perform(multipart("/inspections")
                        .file(jpg(1024))
                        .param("labName", "化学实验楼 A302")
                        .param("inspector", "张三"))
                .andExpect(status().isOk())
                .andExpect(view().name("inspection-form"))
                .andExpect(content().string(containsString("AI 分析失败：TIMEOUT")))
                .andExpect(content().string(containsString("提交并开始 AI 分析")));
    }

    private static MockMultipartFile jpg(long size) {
        return new MockMultipartFile("file", "scene.jpg", "image/jpeg", new byte[(int) size]);
    }
}
