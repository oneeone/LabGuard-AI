package com.labguard.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labguard.model.AiResultSource;
import com.labguard.model.AiReview;
import com.labguard.model.Comparability;
import com.labguard.model.EvidenceCheck;
import com.labguard.model.ReviewOutcome;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * AI 核验记录仓储。逐点核验结果以 JSON 存在单列里，避免为演示引入多表关联；
 * 工单状态不在这里更新，核验结论只供人工参考。
 */
@Repository
public class AiReviewRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final RowMapper<AiReview> ROW_MAPPER = (rs, rowNum) -> new AiReview(
            rs.getLong("id"),
            rs.getLong("work_order_id"),
            ReviewOutcome.valueOf(rs.getString("outcome")),
            rs.getString("evidence"),
            readChecks(rs.getString("checks_json")),
            Comparability.valueOf(rs.getString("comparability")),
            rs.getString("uncertainty"),
            rs.getString("suggested_action"),
            AiResultSource.valueOf(rs.getString("source")),
            rs.getString("model"),
            rs.getString("raw_response"),
            true,
            RowValues.dateTime(rs.getString("created_at"))
    );

    private final JdbcTemplate jdbc;

    public AiReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long insert(AiReview review) {
        String checksJson = writeChecks(review.checks());
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO ai_review
                        (work_order_id, outcome, evidence, checks_json, comparability,
                         uncertainty, suggested_action, source, model, raw_response, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, review.workOrderId());
            ps.setString(2, review.outcome().name());
            ps.setString(3, review.evidence());
            ps.setString(4, checksJson);
            ps.setString(5, review.comparability().name());
            ps.setString(6, review.uncertainty());
            ps.setString(7, review.suggestedAction());
            ps.setString(8, review.source().name());
            ps.setString(9, review.model());
            ps.setString(10, review.rawResponse());
            ps.setString(11, review.createdAt().toString());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("AI 核验结果写入失败：未返回主键");
        }
        return key.longValue();
    }

    public Optional<AiReview> findLatestByWorkOrderId(long workOrderId) {
        List<AiReview> rows = jdbc.query("""
                SELECT id, work_order_id, outcome, evidence, checks_json, comparability,
                       uncertainty, suggested_action, source, model, raw_response, created_at
                FROM ai_review
                WHERE work_order_id = ?
                ORDER BY id DESC
                LIMIT 1
                """, ROW_MAPPER, workOrderId);
        return rows.stream().findFirst();
    }

    private static String writeChecks(List<EvidenceCheck> checks) {
        try {
            return JSON.writeValueAsString(checks);
        } catch (Exception ex) {
            throw new IllegalStateException("AI 核验结果序列化失败", ex);
        }
    }

    /** 历史数据损坏时不阻断详情页渲染，逐点列表降级为空。 */
    private static List<EvidenceCheck> readChecks(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<EvidenceCheck>>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }
}
