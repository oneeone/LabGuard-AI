package com.labguard.service;

import com.labguard.model.AiResultSource;
import com.labguard.model.AuditEvent;
import com.labguard.model.Hazard;
import com.labguard.model.Inspection;
import com.labguard.model.RiskLevel;
import com.labguard.repository.AuditEventRepository;
import com.labguard.repository.HazardRepository;
import com.labguard.repository.InspectionRepository;
import com.labguard.repository.WorkOrderRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 工单业务规则测试：验证建单前置条件、整改与复查的强校验，
 * 以及每次状态变更是否写入审计。
 */
@SpringBootTest(classes = WorkOrderServiceTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/workorder-service-test.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql"
})
class WorkOrderServiceTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({InspectionRepository.class, HazardRepository.class, WorkOrderRepository.class,
            AuditEventRepository.class, HazardService.class, WorkOrderService.class})
    static class TestConfig {
    }

    @Autowired
    private InspectionRepository inspectionRepository;
    @Autowired
    private HazardRepository hazardRepository;
    @Autowired
    private HazardService hazardService;
    @Autowired
    private WorkOrderService workOrderService;
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
    @DisplayName("未确认的隐患不能建单")
    void rejectsUnconfirmedHazard() {
        assertThatThrownBy(() -> workOrderService.create(hazardId, "李四",
                LocalDate.now().plusDays(3), null, "张三"))
                .isInstanceOfSatisfying(WorkflowException.class,
                        ex -> assertThat(ex.code())
                                .isEqualTo(WorkflowException.Code.HAZARD_NOT_CONFIRMED));
    }

    @Test
    @DisplayName("同一隐患重复建单被拒绝")
    void rejectsDuplicateWorkOrder() {
        createConfirmedOrder();

        assertThatThrownBy(() -> workOrderService.create(hazardId, "王五",
                LocalDate.now().plusDays(5), null, "张三"))
                .isInstanceOfSatisfying(WorkflowException.class,
                        ex -> assertThat(ex.code())
                                .isEqualTo(WorkflowException.Code.HAZARD_ALREADY_HAS_ORDER));
    }

    @Test
    @DisplayName("没有整改照片不能提交整改")
    void rejectsRemediationWithoutPhoto() {
        long orderId = createConfirmedOrder();

        assertThatThrownBy(() -> workOrderService.submitRemediation(orderId, "已清理", "  ", "李四"))
                .isInstanceOfSatisfying(WorkflowException.class,
                        ex -> assertThat(ex.code()).isEqualTo(WorkflowException.Code.INVALID_ARGUMENT));
    }

    @Test
    @DisplayName("复查退回必须填写意见")
    void rejectsReturnWithoutComment() {
        long orderId = createConfirmedOrder();
        workOrderService.submitRemediation(orderId, "已清理杂物", "/uploads/after.jpg", "李四");

        assertThatThrownBy(() -> workOrderService.review(orderId, false, " ", "王五"))
                .isInstanceOfSatisfying(WorkflowException.class,
                        ex -> assertThat(ex.code()).isEqualTo(WorkflowException.Code.INVALID_ARGUMENT));
    }

    @Test
    @DisplayName("工单关闭后不能再提交整改")
    void rejectsRemediationAfterClosed() {
        long orderId = createConfirmedOrder();
        workOrderService.submitRemediation(orderId, "已清理杂物", "/uploads/after.jpg", "李四");
        workOrderService.review(orderId, true, "复查通过", "王五");

        assertThatThrownBy(() -> workOrderService.submitRemediation(
                orderId, "再次整改", "/uploads/b.jpg", "李四"))
                .isInstanceOfSatisfying(WorkflowException.class,
                        ex -> assertThat(ex.code())
                                .isEqualTo(WorkflowException.Code.ILLEGAL_TRANSITION));
    }

    @Test
    @DisplayName("完整流程按顺序写入三条审计记录")
    void writesAuditTrail() {
        long orderId = createConfirmedOrder();
        workOrderService.submitRemediation(orderId, "已清理杂物", "/uploads/after.jpg", "李四");
        workOrderService.review(orderId, true, "复查通过", "王五");

        List<AuditEvent> timeline = workOrderService.timeline(orderId);
        assertThat(timeline).extracting(AuditEvent::action)
                .containsExactly("CREATE_WORK_ORDER", "SUBMIT_REMEDIATION", "PASS_REVIEW");
        assertThat(timeline).extracting(AuditEvent::toStatus)
                .containsExactly("PENDING_REMEDIATION", "PENDING_REVIEW", "CLOSED");
    }

    private long createConfirmedOrder() {
        hazardService.confirm(hazardId, "张三");
        return workOrderService.create(hazardId, "李四",
                LocalDate.now().plusDays(3), null, "张三");
    }
}
