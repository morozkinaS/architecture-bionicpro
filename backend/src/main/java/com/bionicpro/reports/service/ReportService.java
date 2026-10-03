package com.bionicpro.reports.service;

import com.bionicpro.reports.exception.ReportNotReadyException;
import com.bionicpro.reports.model.Report;
import com.bionicpro.reports.model.ReportRow;
import com.bionicpro.reports.repository.ReportRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Сервис отчётов: формирует отчёт пользователя из OLAP-витрины.
 * Отчёт доступен только за период, уже обработанный ETL-процессом (Airflow).
 */
@Service
public class ReportService {

    private final ReportRepository reportRepository;

    public ReportService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    public Report getReport(String userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("Период отчёта задан некорректно: from позже to");
        }

        List<ReportRow> rows = reportRepository.findReportRows(userId, from, to);
        if (rows.isEmpty()) {
            throw new ReportNotReadyException(buildNotReadyMessage(userId, from, to));
        }
        return new Report(userId, from, to, Instant.now(), rows);
    }

    private String buildNotReadyMessage(String userId, LocalDate from, LocalDate to) {
        Optional<LocalDate> minDate = reportRepository.findMinReportDate(userId);
        if (minDate.isEmpty()) {
            return "Отчёт ещё не сформирован: данные по вашему протезу появятся после обработки "
                    + "ETL-процессом (ежедневно в 03:00).";
        }
        Optional<LocalDate> maxDate = reportRepository.findMaxReportDate(userId);
        return String.format(
                "Данные за запрошенный период (%s — %s) ещё не готовы. Доступный период: %s — %s.",
                from, to, minDate.get(), maxDate.orElse(minDate.get()));
    }
}