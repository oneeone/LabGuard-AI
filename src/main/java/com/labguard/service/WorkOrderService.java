package com.labguard.service;

import com.labguard.model.Hazard;
import com.labguard.model.WorkOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WorkOrderService {
    private final JdbcTemplate jdbc;

    private final RowMapper<WorkOrder> mapper = (rs, rowNum) -> new WorkOrder(
            rs.getLong("id"),
            rs.getString("category"),
            rs.getString("risk_level"),
            rs.getString("location"),
            rs.getString("suggested_action"),
            rs.getString("owner"),
            rs.getString("status"),
            rs.getString("rectification_note"),
            rs.getString("review_note")
    );

    public WorkOrderService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long create(Hazard hazard, String owner) {
        jdbc.update("""
                INSERT INTO work_orders
                (category, risk_level, location, evidence, suggested_action, owner, status)
                VALUES (?, ?, ?, ?, ?, ?, 'PENDING_REMEDIATION')
                """,
                hazard.category(), hazard.riskLevel(), hazard.location(), hazard.evidence(),
                hazard.suggestedAction(), (owner == null || owner.isBlank()) ? "待分配" : owner.trim());
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public List<WorkOrder> findAll() {
        return jdbc.query("SELECT * FROM work_orders ORDER BY id DESC", mapper);
    }

    public void submitRectification(long id, String note) {
        if (note == null || note.isBlank()) throw new IllegalArgumentException("整改说明不能为空");
        int updated = jdbc.update("""
                UPDATE work_orders SET status='PENDING_REVIEW', rectification_note=?, updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='PENDING_REMEDIATION'
                """, note.trim(), id);
        if (updated == 0) throw new IllegalArgumentException("工单不存在或当前状态不能提交整改");
    }

    public void review(long id, boolean passed, String note) {
        String status = passed ? "CLOSED" : "PENDING_REMEDIATION";
        int updated = jdbc.update("""
                UPDATE work_orders SET status=?, review_note=?,
                    rectification_note=CASE WHEN ? THEN rectification_note ELSE '' END,
                    updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND status='PENDING_REVIEW'
                """, status, (note == null || note.isBlank()) ? "人工复查" : note.trim(), passed, id);
        if (updated == 0) throw new IllegalArgumentException("工单不存在或当前状态不能复查");
    }
}
