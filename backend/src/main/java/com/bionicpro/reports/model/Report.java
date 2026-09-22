package com.bionicpro.reports.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Отчёт о работе протеза пользователя за запрошенный период.
 */
public record Report(
        String userId,
        LocalDate from,
        LocalDate to,
        Instant generatedAt,
        List<ReportRow> rows
) {
}