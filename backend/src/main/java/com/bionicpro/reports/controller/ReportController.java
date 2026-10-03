package com.bionicpro.reports.controller;

import com.bionicpro.reports.model.Report;
import com.bionicpro.reports.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Эндпоинт отчётности.
 * Доступ только для аутентифицированных пользователей (JWT).
 * Пользователь получает отчёт только по своим данным: user_id берётся из sub токена.
 */
@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping
    public ResponseEntity<?> getReport(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "json") String format) {

        Report report = reportService.getReport(jwt.getSubject(), from, to);

        if ("csv".equalsIgnoreCase(format)) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header("Content-Disposition", "attachment; filename=report.csv")
                    .body(toCsv(report));
        }
        return ResponseEntity.ok(report);
    }

    private String toCsv(Report report) {
        StringBuilder sb = new StringBuilder();
        sb.append("report_date,prosthesis_id,total_activations,avg_response_time_ms,"
                + "max_response_time_ms,min_response_time_ms,avg_battery_level,total_usage_minutes\n");
        report.rows().forEach(row -> sb.append(row.reportDate())
                .append(',').append(row.prosthesisId())
                .append(',').append(row.totalActivations())
                .append(',').append(row.avgResponseTimeMs())
                .append(',').append(row.maxResponseTimeMs())
                .append(',').append(row.minResponseTimeMs())
                .append(',').append(row.avgBatteryLevel())
                .append(',').append(row.totalUsageMinutes())
                .append('\n'));
        return sb.toString();
    }
}