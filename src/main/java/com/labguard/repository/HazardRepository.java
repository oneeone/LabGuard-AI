package com.labguard.repository;

import com.labguard.model.Hazard;
import com.labguard.model.HazardStatus;
import com.labguard.model.RiskLevel;
import com.labguard.model.RiskLevelSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 隐患排查仓储。人工确认与驳回都使用条件更新，
 * 以受影响行数判断是否成功，防止重复提交。
 */
@Repository
public class HazardRepository {

    private static final RowMapper<Hazard> ROW_MAPPER = (rs, rowNum) -> new Hazard(
            rs.getLong("id"),
            rs.getLong("inspection_id"),
            rs.getString("category"),
            RiskLevel.valueOf(rs.getString("risk_level")),
            RiskLevelSource.valueOf(rs.getString("risk_level_source")),
            rs.getString("location"),
            rs.getString("evidence"),
            RowValues.nullableDouble(rs, "confidence"),
            rs.getString("uncertainty"),
            rs.getString("suggested_action"),
            RowValues.bool(rs.getInt("needs_human_review")),
            HazardStatus.valueOf(rs.getString("status")),
            rs.getString("review_note"),
            rs.getString("confirmed_by"),
            RowValues.dateTime(rs.getString("confirmed_at")),
            RowValues.dateTime(rs.getString("created_at"))
    );

    private final JdbcTemplate jdbc;

    public HazardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(Hazard hazard) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO hazard
                        (inspection_id, category, risk_level, risk_level_source, location,
                         evidence, confidence, uncertainty, suggested_action, needs_human_review,
                         status, review_note, confirmed_by, confirmed_at, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, hazard.inspectionId());
            ps.setString(2, hazard.category());
            ps.setString(3, hazard.riskLevel().name());
            ps.setString(4, hazard.riskLevelSource().name());
            ps.setString(5, hazard.location());
            ps.setString(6, hazard.evidence());
            if (hazard.confidence() == null) {
                ps.setNull(7, java.sql.Types.REAL);
            } else {
                ps.setDouble(7, hazard.confidence());
            }
            ps.setString(8, hazard.uncertainty());
            ps.setString(9, hazard.suggestedAction());
            ps.setInt(10, hazard.needsHumanReview() ? 1 : 0);
            ps.setString(11, hazard.status().name());
            ps.setString(12, hazard.reviewNote());
            ps.setString(13, hazard.confirmedBy());
            ps.setString(14, hazard.confirmedAt() == null ? null : hazard.confirmedAt().toString());
            ps.setString(15, hazard.createdAt().toString());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("隐患记录写入后未返回主键");
        }
        return key.longValue();
    }

    public Optional<Hazard> findById(long id) {
        return jdbc.query("SELECT * FROM hazard WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    public List<Hazard> findByInspectionId(long inspectionId) {
        return jdbc.query("SELECT * FROM hazard WHERE inspection_id = ? ORDER BY id",
                ROW_MAPPER, inspectionId);
    }

    /**
     * 确认隐患（CONFIRM 或 EDIT）。允许同时修正类别、等级、位置、证据与建议措施。
     * 仅当当前状态为 PENDING_CONFIRM 时生效。
     *
     * @return 受影响行数，0 表示状态已被改变
     */
    public int confirm(Hazard hazard, String operator, LocalDateTime confirmedAt) {
        return jdbc.update("""
                UPDATE hazard
                   SET category = ?, risk_level = ?, risk_level_source = ?, location = ?,
                       evidence = ?, suggested_action = ?, status = ?,
                       review_note = NULL, confirmed_by = ?, confirmed_at = ?
                 WHERE id = ? AND status = ?
                """,
                hazard.category(),
                hazard.riskLevel().name(),
                hazard.riskLevelSource().name(),
                hazard.location(),
                hazard.evidence(),
                hazard.suggestedAction(),
                HazardStatus.CONFIRMED.name(),
                operator,
                confirmedAt.toString(),
                hazard.id(),
                HazardStatus.PENDING_CONFIRM.name());
    }

    /**
     * 驳回隐患。仅当当前状态为 PENDING_CONFIRM 时生效。
     *
     * @return 受影响行数，0 表示状态已被改变
     */
    public int reject(long id, String reviewNote, String operator, LocalDateTime confirmedAt) {
        return jdbc.update("""
                UPDATE hazard
                   SET status = ?, review_note = ?, confirmed_by = ?, confirmed_at = ?
                 WHERE id = ? AND status = ?
                """,
                HazardStatus.REJECTED.name(),
                reviewNote,
                operator,
                confirmedAt.toString(),
                id,
                HazardStatus.PENDING_CONFIRM.name());
    }
}
