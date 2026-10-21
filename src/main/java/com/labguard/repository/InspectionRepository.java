package com.labguard.repository;

import com.labguard.model.AiResultSource;
import com.labguard.model.AnalysisStatus;
import com.labguard.model.Inspection;
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
 * 巡检记录仓储。只做数据读写，不包含业务规则。
 */
@Repository
public class InspectionRepository {

    private static final RowMapper<Inspection> ROW_MAPPER = (rs, rowNum) -> new Inspection(
            rs.getLong("id"),
            rs.getString("lab_name"),
            rs.getString("inspector"),
            RowValues.dateTime(rs.getString("inspected_at")),
            rs.getString("image_path"),
            AiResultSource.valueOf(rs.getString("analysis_source")),
            AnalysisStatus.valueOf(rs.getString("analysis_status")),
            rs.getString("ai_raw_response"),
            rs.getString("note"),
            RowValues.dateTime(rs.getString("created_at"))
    );

    private final JdbcTemplate jdbc;

    public InspectionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 插入巡检记录并返回数据库生成的主键。 */
    public long insert(Inspection inspection) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO inspection
                        (lab_name, inspector, inspected_at, image_path, analysis_source,
                         analysis_status, ai_raw_response, note, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, inspection.labName());
            ps.setString(2, inspection.inspector());
            ps.setString(3, inspection.inspectedAt().toString());
            ps.setString(4, inspection.imagePath());
            ps.setString(5, inspection.analysisSource().name());
            ps.setString(6, inspection.analysisStatus().name());
            ps.setString(7, inspection.aiRawResponse());
            ps.setString(8, inspection.note());
            ps.setString(9, inspection.createdAt().toString());
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("巡检记录写入后未返回主键");
        }
        return key.longValue();
    }

    public Optional<Inspection> findById(long id) {
        return jdbc.query("SELECT * FROM inspection WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    /** 分析完成后回写状态与 AI 原始响应。 */
    public void updateAnalysisResult(long id, AnalysisStatus status, String rawResponse) {
        jdbc.update("UPDATE inspection SET analysis_status = ?, ai_raw_response = ? WHERE id = ?",
                status.name(), rawResponse, id);
    }

    public List<Inspection> findAll() {
        return jdbc.query("SELECT * FROM inspection ORDER BY id DESC", ROW_MAPPER);
    }
}
