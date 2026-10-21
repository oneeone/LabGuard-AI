package com.labguard.repository;

import com.labguard.model.AiResultSource;
import com.labguard.model.AuditEvent;
import com.labguard.model.Hazard;
import com.labguard.model.HazardStatus;
import com.labguard.model.Inspection;
import com.labguard.model.RiskLevel;
import com.labguard.model.WorkOrder;
import com.labguard.model.WorkOrderDetail;
import com.labguard.model.WorkOrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据层集成测试：使用真实 SQLite 文件（不是内存库），
 * 验证四表读写、条件更新与审计追加。
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:sqlite:target/workflow-persistence.db",
        "spring.datasource.driver-class-name=org.sqlite.JDBC",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema.sql"
})
@Import({InspectionRepository.class, HazardRepository.class,
        WorkOrderRepository.class, AuditEventRepository.class})
class WorkflowPersistenceTest {

    private static final String DB_URL = "jdbc:sqlite:target/workflow-persistence.db";

    @Autowired
    private InspectionRepository inspectionRepository;
    @Autowired
    private HazardRepository hazardRepository;
    @Autowired
    private WorkOrderRepository workOrderRepository;
    @Autowired
    private AuditEventRepository auditEventRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanTables() {
        jdbc.update("DELETE FROM audit_event");
        jdbc.update("DELETE FROM work_order");
        jdbc.update("DELETE FROM hazard");
        jdbc.update("DELETE FROM inspection");
    }

    @Test
    @DisplayName("巡检 → 隐患 → 工单可完整写入并读回")
    void persistsFullChain() {
        long inspectionId = inspectionRepository.insert(inspection());
        long hazardId = hazardRepository.insert(hazard(inspectionId));

        Hazard stored = hazardRepository.findById(hazardId).orElseThrow();
        assertThat(stored.inspectionId()).isEqualTo(inspectionId);
        assertThat(stored.status()).isEqualTo(HazardStatus.PENDING_CONFIRM);
        assertThat(stored.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(stored.needsHumanReview()).isTrue();

        assertThat(hazardRepository.confirm(stored, "张三", LocalDateTime.now())).isEqualTo(1);

        long orderId = workOrderRepository.insert(
                WorkOrder.create(hazardId, "李四", LocalDate.now().plusDays(3), "尽快清理"));

        WorkOrderDetail detail = workOrderRepository.findDetailById(orderId).orElseThrow();
        assertThat(detail.hazard().category()).isEqualTo("BLOCKED_EXIT");
        assertThat(detail.order().assignee()).isEqualTo("李四");
        assertThat(detail.labName()).isEqualTo("材料实验室");
        assertThat(detail.order().status()).isEqualTo(WorkOrderStatus.PENDING_REMEDIATION);
    }

    @Test
    @DisplayName("重复确认同一隐患时第二次更新 0 行")
    void confirmRejectsSecondAttempt() {
        long inspectionId = inspectionRepository.insert(inspection());
        long hazardId = hazardRepository.insert(hazard(inspectionId));
        Hazard stored = hazardRepository.findById(hazardId).orElseThrow();

        assertThat(hazardRepository.confirm(stored, "张三", LocalDateTime.now())).isEqualTo(1);
        assertThat(hazardRepository.confirm(stored, "张三", LocalDateTime.now())).isZero();
        assertThat(hazardRepository.findById(hazardId).orElseThrow().status())
                .isEqualTo(HazardStatus.CONFIRMED);
    }

    @Test
    @DisplayName("整改提交与复查关闭后状态和审计可追溯")
    void recordsRemediationAndReview() {
        long inspectionId = inspectionRepository.insert(inspection());
        long hazardId = hazardRepository.insert(hazard(inspectionId));
        hazardRepository.confirm(hazardRepository.findById(hazardId).orElseThrow(),
                "张三", LocalDateTime.now());
        long orderId = workOrderRepository.insert(
                WorkOrder.create(hazardId, "李四", LocalDate.now().plusDays(3), null));

        assertThat(workOrderRepository.submitRemediation(orderId, "已清理杂物", "/uploads/after.jpg"))
                .isEqualTo(1);
        assertThat(workOrderRepository.finishReview(orderId, WorkOrderStatus.CLOSED, "复查通过", "王五"))
                .isEqualTo(1);
        assertThat(workOrderRepository.findById(orderId).orElseThrow().status())
                .isEqualTo(WorkOrderStatus.CLOSED);

        auditEventRepository.insert(AuditEvent.of(
                AuditEvent.ENTITY_WORK_ORDER, orderId, "PASS_REVIEW",
                WorkOrderStatus.PENDING_REVIEW.name(), WorkOrderStatus.CLOSED.name(),
                "王五", "复查通过", "{\"afterImage\":\"/uploads/after.jpg\"}"));

        List<AuditEvent> timeline = auditEventRepository.findByEntity(
                AuditEvent.ENTITY_WORK_ORDER, orderId);
        assertThat(timeline).hasSize(1);
        assertThat(timeline.get(0).toStatus()).isEqualTo(WorkOrderStatus.CLOSED.name());
        assertThat(timeline.get(0).detail()).contains("after.jpg");
    }

    @Test
    @DisplayName("数据真正落到 SQLite 文件，换一个连接仍能读到")
    void dataSurvivesNewConnection() throws Exception {
        long inspectionId = inspectionRepository.insert(inspection());

        try (Connection connection = DriverManager.getConnection(DB_URL);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT lab_name FROM inspection WHERE id = " + inspectionId)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).isEqualTo("材料实验室");
        }
    }

    private static Inspection inspection() {
        return Inspection.pending("材料实验室", "张三", LocalDateTime.now(),
                "/uploads/original.jpg", AiResultSource.DEMO, null);
    }

    private static Hazard hazard(long inspectionId) {
        return Hazard.fromAi(inspectionId, "BLOCKED_EXIT", RiskLevel.HIGH, "东侧安全出口",
                "纸箱堆放在出口前，占用疏散通道", 0.92, null, "移除杂物并保持通道畅通", true);
    }
}
