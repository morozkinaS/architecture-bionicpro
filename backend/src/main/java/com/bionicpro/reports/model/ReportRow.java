package com.bionicpro.reports.model;

import java.time.LocalDate;

/**
 * Строка отчёта о работе протеза за один день.
 * Данные берутся из OLAP-витрины reports_mart (ClickHouse) без вычислений в реальном времени.
 */
public record ReportRow(
        LocalDate reportDate,
        String prosthesisId,
        long totalActivations,
        double avgResponseTimeMs,
        long maxResponseTimeMs,
        long minResponseTimeMs,
        double avgBatteryLevel,
        long totalUsageMinutes
) {
}