package com.labguard.service;

import com.labguard.model.AiResultSource;
import com.labguard.model.AuditEvent;
import com.labguard.model.Hazard;
import com.labguard.model.HazardStatus;
import com.labguard.model.Inspection;
import com.labguard.model.RiskLevel;
import com.labguard.model.RiskLevelSource;
import com.labguard.repository.AuditEventRepository;
import com.labguard.repository.HazardRepository;
import com.labguard.repository.InspectionRepository;
import com.labguard.workflow.WorkflowException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 隐患人工确认识别规则测试：确认、修改、驳回都只能从待确认出发，
 * 驳回后不可恢复，每次变更都写入审计。
 */
@SpringBootTest(classes = HazardServiceTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/hazard-service-test.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql"
})
class HazardServiceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({InspectionRepository.class, HazardRepository.class, AuditEventRepository.class,
            HazardService.class})
    static class TestConfig {
    }

    @Autowired
    private InspectionRepository inspectionRepository;
    @Autowired
    private HazardRepository hazardRepository;
    @Autowired
    private HazardService hazardService;
    @Autowired
    private JdbcTemplate jdbc;

    private long hazardId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM work_order");
        jdbc.update("DELETE FROM hazard");
        jdbc.update("DELETE FROM inspection");
        long inspectionId = inspectionRepository.insert(Inspection.pending(
                "材料实验室", "张三", LocalDateTime.now(), "/uploads/a.jpg",
                AiResultSource.DEMO, null));
        hazardId = hazardRepository.insert(Hazard.fromAi(inspectionId, "BLOCKED_EXIT",
                RiskLevel.HIGH, "东侧出口", "纸箱阻挡疏散通道", 0.9, null, "移除杂物", true));
    }

    @Test
    @DisplayName("确认后状态变为已确认并写入审计")
    void confirmUpdatesStatusAndWritesAudit() {
        hazardService.confirm(hazardId, "张三");

        assertThat(hazardRepository.findById(hazardId).orElseThrow().status())
                .isEqualTo(HazardStatus.CONFIRMED);
        assertThat(hazardService.timeline(hazardId))
                .extracting(AuditEvent::action)
                .containsExactly("CONFIRM_HAZARD");
    }

    @Test
    @DisplayName("重复确认被拒绝")
    void rejectsSecondConfirm() {
        hazardService.confirm(hazardId, "张三");

        assertThatThrownBy(() -> hazardService.confirm(hazardId, "李四"))
                .isInstanceOfSatisfying(WorkflowException.class,
                        ex -> assertThat(ex.code())
                                .isEqualTo(WorkflowException.Code.ILLEGAL_TRANSITION));
    }

    @Test
    @DisplayName("驳回必须填写理由")
    void rejectsRejectWithoutNote() {
        assertThatThrownBy(() -> hazardService.reject(hazardId, "  ", "张三"))
                .isInstanceOfSatisfying(WorkflowException.class,
                        ex -> assertThat(ex.code()).isEqualTo(WorkflowException.Code.INVALID_ARGUMENT));
    }

    @Test
    @DisplayName("驳回后不能再次确认")
    void rejectsConfirmAfterReject() {
        hazardService.reject(hazardId, "证据不足，非真实风险", "张三");

        assertThat(hazardRepository.findById(hazardId).orElseThrow().status())
                .isEqualTo(HazardStatus.REJECTED);
        assertThatThrownBy(() -> hazardService.confirm(hazardId, "李四"))
                .isInstanceOfSatisfying(WorkflowException.class,
                        ex -> assertThat(ex.code())
                                .isEqualTo(WorkflowException.Code.ILLEGAL_TRANSITION));
    }

    @Test
    @DisplayName("修改后确认：等级来源标记为人工调整")
    void editMarksManualSource() {
        hazardService.edit(hazardId, new HazardEditCommand("BLOCKED_EXIT", RiskLevel.LOW,
                "东侧出口", "纸箱阻挡疏散通道", "当日清理完毕"), "张三");

        Hazard updated = hazardRepository.findById(hazardId).orElseThrow();
        assertThat(updated.status()).isEqualTo(HazardStatus.CONFIRMED);
        assertThat(updated.riskLevelSource()).isEqualTo(RiskLevelSource.MANUAL);
        assertThat(updated.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(updated.suggestedAction()).isEqualTo("当日清理完毕");
    }

    @Test
    @DisplayName("修改时必填项为空被拒绝")
    void rejectsEditWithBlankField() {
        assertThatThrownBy(() -> hazardService.edit(hazardId,
                new HazardEditCommand("BLOCKED_EXIT", RiskLevel.LOW, " ", "证据", "清理"), "张三"))
                .isInstanceOfSatisfying(WorkflowException.class,
                        ex -> assertThat(ex.code()).isEqualTo(WorkflowException.Code.INVALID_ARGUMENT));
    }
}
