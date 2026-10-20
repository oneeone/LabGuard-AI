package com.labguard.repository;

import com.labguard.model.AuditEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * 操作审计仓储。每次状态变化追加一条记录，不做更新与删除。
 */
@Repository
public class AuditEventRepository {

    private static final RowMapper<AuditEvent> ROW_MAPPER = (rs, rowNum) -> new AuditEvent(
            rs.getLong("id"),
            rs.getString("entity_type"),
            rs.getLong("entity_id"),
            rs.getString("action"),
            rs.getString("from_status"),
            rs.getString("to_status"),
            rs.getString("operator"),
            rs.getString("comment"),
            rs.getString("detail"),
            RowValues.dateTime(rs.getString("created_at"))
    );

    private final JdbcTemplate jdbc;

    public AuditEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(AuditEvent event) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO audit_event
                        (entity_type, entity_id, action, from_status, to_status,
                         operator, comment, detail, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, event.entityType());
            ps.setLong(2, event.entityId());
            ps.setString(3, event.action());
            ps.setString(4, event.fromStatus());
            ps.setString(5, event.toStatus());
            ps.setString(6, event.operator());
            ps.setString(7, event.comment());
            ps.setString(8, event.detail());
            ps.setString(9, event.createdAt().toString());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("审计记录写入后未返回主键");
        }
        return key.longValue();
    }

    /** 按对象查询时间线，用于工单详情页的历史区块。 */
    public List<AuditEvent> findByEntity(String entityType, long entityId) {
        return jdbc.query("""
                SELECT * FROM audit_event
                 WHERE entity_type = ? AND entity_id = ?
                 ORDER BY id
                """, ROW_MAPPER, entityType, entityId);
    }
}
