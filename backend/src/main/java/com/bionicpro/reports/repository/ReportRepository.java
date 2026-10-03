package com.bionicpro.reports.repository;

import com.bionicpro.reports.model.ReportRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Доступ к OLAP-витрине отчётности reports_mart (ClickHouse).
 * Все запросы фильтруются по user_id — пользователь видит только свои данные.
 */
@Repository
public class ReportRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReportRow> findReportRows(String userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT report_date, prosthesis_id, total_activations,
                       avg_response_time_ms, max_response_time_ms, min_response_time_ms,
                       avg_battery_level, total_usage_minutes
                FROM reports_mart
                WHERE user_id = ? AND report_date BETWEEN ? AND ?
                ORDER BY report_date, prosthesis_id
                """;
        return jdbcTemplate.query(sql, (rs, rowNum) -> new ReportRow(
                rs.getDate("report_date").toLocalDate(),
                rs.getString("prosthesis_id"),
                rs.getLong("total_activations"),
                rs.getDouble("avg_response_time_ms"),
                rs.getLong("max_response_time_ms"),
                rs.getLong("min_response_time_ms"),
                rs.getDouble("avg_battery_level"),
                rs.getLong("total_usage_minutes")
        ), userId, from, to);
    }

    public Optional<LocalDate> findMinReportDate(String userId) {
        return findSingleDate("SELECT min(report_date) FROM reports_mart WHERE user_id = ?", userId);
    }

    public Optional<LocalDate> findMaxReportDate(String userId) {
        return findSingleDate("SELECT max(report_date) FROM reports_mart WHERE user_id = ?", userId);
    }

    private Optional<LocalDate> findSingleDate(String sql, String userId) {
        List<LocalDate> dates = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getDate(1).toLocalDate(), userId);
        return dates.stream().findFirst();
    }
}