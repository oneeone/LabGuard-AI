package com.labguard.repository;

import com.labguard.model.AiResultSource;
import com.labguard.model.AnalysisStatus;
import com.labguard.model.Hazard;
import com.labguard.model.HazardStatus;
import com.labguard.model.Inspection;
import com.labguard.model.RiskLevel;
import com.labguard.model.RiskLevelSource;
import com.labguard.model.WorkOrder;
import com.labguard.model.WorkOrderDetail;
import com.labguard.model.WorkOrderStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 整改工单仓储。除单表读写外，提供工单 + 隐患 + 巡检的关联查询，
 * 供工单列表页与详情页一次性取数。状态更新一律使用条件更新。
 */
@Repository
public class WorkOrderRepository {

    private static final RowMapper<WorkOrder> ROW_MAPPER = (rs, rowNum) -> new WorkOrder(
            rs.getLong("id"),
            rs.getLong("hazard_id"),
            rs.getString("assignee"),
            RowValues.date(rs.getString("due_date")),
            WorkOrderStatus.valueOf(rs.getString("status")),
            rs.getString("note"),
            rs.getString("remediation_note"),
            rs.getString("after_image_path"),
            rs.getString("review_comment"),
            rs.getString("reviewed_by"),
            RowValues.dateTime(rs.getString("reviewed_at")),
            RowValues.dateTime(rs.getString("created_at")),
            RowValues.dateTime(rs.getString("updated_at"))
    );

    private static final String DETAIL_SELECT = """
            SELECT
                w.id                 AS w_id,
                w.hazard_id          AS w_hazard_id,
                w.assignee           AS w_assignee,
                w.due_date           AS w_due_date,
                w.status             AS w_status,
                w.note               AS w_note,
                w.remediation_note   AS w_remediation_note,
                w.after_image_path   AS w_after_image_path,
                w.review_comment     AS w_review_comment,
                w.reviewed_by        AS w_reviewed_by,
                w.reviewed_at        AS w_reviewed_at,
                w.created_at         AS w_created_at,
                w.updated_at         AS w_updated_at,
                h.id                 AS h_id,
                h.inspection_id      AS h_inspection_id,
                h.category           AS h_category,
                h.risk_level         AS h_risk_level,
                h.risk_level_source  AS h_risk_level_source,
                h.location           AS h_location,
                h.evidence           AS h_evidence,
                h.confidence         AS h_confidence,
                h.uncertainty        AS h_uncertainty,
                h.suggested_action   AS h_suggested_action,
                h.needs_human_review AS h_needs_human_review,
                h.status             AS h_status,
                h.review_note        AS h_review_note,
                h.confirmed_by       AS h_confirmed_by,
                h.confirmed_at       AS h_confirmed_at,
                h.created_at         AS h_created_at,
                i.id                 AS i_id,
                i.lab_name           AS i_lab_name,
                i.inspector          AS i_inspector,
                i.inspected_at       AS i_inspected_at,
                i.image_path         AS i_image_path,
                i.analysis_source    AS i_analysis_source,
                i.analysis_status    AS i_analysis_status,
                i.ai_raw_response    AS i_ai_raw_response,
                i.note               AS i_note,
                i.created_at         AS i_created_at
            FROM work_order w
            JOIN hazard h     ON h.id = w.hazard_id
            JOIN inspection i ON i.id = h.inspection_id
            """;

    private static final RowMapper<WorkOrderDetail> DETAIL_MAPPER = (rs, rowNum) ->
            new WorkOrderDetail(mapOrder(rs), mapHazard(rs), mapInspection(rs));

    private final JdbcTemplate jdbc;

    public WorkOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(WorkOrder order) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO work_order
                        (hazard_id, assignee, due_date, status, note, remediation_note,
                         after_image_path, review_comment, reviewed_by, reviewed_at,
                         created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, order.hazardId());
            ps.setString(2, order.assignee());
            ps.setString(3, order.dueDate().toString());
            ps.setString(4, order.status().name());
            ps.setString(5, order.note());
            ps.setString(6, order.remediationNote());
            ps.setString(7, order.afterImagePath());
            ps.setString(8, order.reviewComment());
            ps.setString(9, order.reviewedBy());
            ps.setString(10, order.reviewedAt() == null ? null : order.reviewedAt().toString());
            ps.setString(11, order.createdAt().toString());
            ps.setString(12, order.updatedAt().toString());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("工单写入后未返回主键");
        }
        return key.longValue();
    }

    public Optional<WorkOrder> findById(long id) {
        return jdbc.query("SELECT * FROM work_order WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    /** 同一隐患只允许一张工单，创建前用于幂等判断。 */
    public boolean existsByHazardId(long hazardId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_order WHERE hazard_id = ?", Integer.class, hazardId);
        return count != null && count > 0;
    }

    /** 工单列表。status 为 null 时返回全部。 */
    public List<WorkOrderDetail> findDetails(WorkOrderStatus status) {
        String sql = DETAIL_SELECT + (status == null ? "" : " WHERE w.status = ?")
                + " ORDER BY w.id DESC";
        if (status == null) {
            return jdbc.query(sql, DETAIL_MAPPER);
        }
        return jdbc.query(sql, DETAIL_MAPPER, status.name());
    }

    public Optional<WorkOrderDetail> findDetailById(long id) {
        return jdbc.query(DETAIL_SELECT + " WHERE w.id = ?", DETAIL_MAPPER, id)
                .stream()
                .findFirst();
    }

    /**
     * 提交整改。仅当当前状态为 PENDING_REMEDIATION 时生效。
     *
     * @return 受影响行数，0 表示状态已变化或工单不存在
     */
    public int submitRemediation(long id, String remediationNote, String afterImagePath) {
        String now = LocalDateTime.now().toString();
        return jdbc.update("""
                UPDATE work_order
                   SET status = ?, remediation_note = ?, after_image_path = ?, updated_at = ?
                 WHERE id = ? AND status = ?
                """,
                WorkOrderStatus.PENDING_REVIEW.name(),
                remediationNote,
                afterImagePath,
                now,
                id,
                WorkOrderStatus.PENDING_REMEDIATION.name());
    }

    /**
     * 复查结束（通过或退回）。仅当当前状态为 PENDING_REVIEW 时生效。
     *
     * @param target CLOSED 或 PENDING_REMEDIATION
     * @return 受影响行数，0 表示状态已变化或工单不存在
     */
    public int finishReview(long id, WorkOrderStatus target, String reviewComment, String operator) {
        String now = LocalDateTime.now().toString();
        return jdbc.update("""
                UPDATE work_order
                   SET status = ?, review_comment = ?, reviewed_by = ?, reviewed_at = ?, updated_at = ?
                 WHERE id = ? AND status = ?
                """,
                target.name(),
                reviewComment,
                operator,
                now,
                now,
                id,
                WorkOrderStatus.PENDING_REVIEW.name());
    }

    private static WorkOrder mapOrder(ResultSet rs) throws SQLException {
        return new WorkOrder(
                rs.getLong("w_id"),
                rs.getLong("w_hazard_id"),
                rs.getString("w_assignee"),
                RowValues.date(rs.getString("w_due_date")),
                WorkOrderStatus.valueOf(rs.getString("w_status")),
                rs.getString("w_note"),
                rs.getString("w_remediation_note"),
                rs.getString("w_after_image_path"),
                rs.getString("w_review_comment"),
                rs.getString("w_reviewed_by"),
                RowValues.dateTime(rs.getString("w_reviewed_at")),
                RowValues.dateTime(rs.getString("w_created_at")),
                RowValues.dateTime(rs.getString("w_updated_at"))
        );
    }

    private static Hazard mapHazard(ResultSet rs) throws SQLException {
        return new Hazard(
                rs.getLong("h_id"),
                rs.getLong("h_inspection_id"),
                rs.getString("h_category"),
                RiskLevel.valueOf(rs.getString("h_risk_level")),
                RiskLevelSource.valueOf(rs.getString("h_risk_level_source")),
                rs.getString("h_location"),
                rs.getString("h_evidence"),
                RowValues.nullableDouble(rs, "h_confidence"),
                rs.getString("h_uncertainty"),
                rs.getString("h_suggested_action"),
                RowValues.bool(rs.getInt("h_needs_human_review")),
                HazardStatus.valueOf(rs.getString("h_status")),
                rs.getString("h_review_note"),
                rs.getString("h_confirmed_by"),
                RowValues.dateTime(rs.getString("h_confirmed_at")),
                RowValues.dateTime(rs.getString("h_created_at"))
        );
    }

    private static Inspection mapInspection(ResultSet rs) throws SQLException {
        return new Inspection(
                rs.getLong("i_id"),
                rs.getString("i_lab_name"),
                rs.getString("i_inspector"),
                RowValues.dateTime(rs.getString("i_inspected_at")),
                rs.getString("i_image_path"),
                AiResultSource.valueOf(rs.getString("i_analysis_source")),
                AnalysisStatus.valueOf(rs.getString("i_analysis_status")),
                rs.getString("i_ai_raw_response"),
                rs.getString("i_note"),
                RowValues.dateTime(rs.getString("i_created_at"))
        );
    }
}
