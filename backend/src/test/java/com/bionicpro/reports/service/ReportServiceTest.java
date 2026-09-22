package com.bionicpro.reports.service;

import com.bionicpro.reports.exception.ReportNotReadyException;
import com.bionicpro.reports.model.Report;
import com.bionicpro.reports.model.ReportRow;
import com.bionicpro.reports.repository.ReportRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private final ReportRepository repository = mock(ReportRepository.class);
    private final ReportService service = new ReportService(repository);

    @Test
    void getReport_returnsRowsForUser() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 7);
        ReportRow row = new ReportRow(
                LocalDate.of(2024, 1, 1), "PR-001", 100, 85.5, 120, 60, 78.3, 540);
        when(repository.findReportRows("user-1", from, to)).thenReturn(List.of(row));

        Report report = service.getReport("user-1", from, to);

        assertEquals("user-1", report.userId());
        assertEquals(1, report.rows().size());
        verify(repository).findReportRows("user-1", from, to);
    }

    @Test
    void getReport_throwsWhenNoDataForPeriod() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 7);
        when(repository.findReportRows(anyString(), any(), any())).thenReturn(List.of());
        when(repository.findMinReportDate("user-1"))
                .thenReturn(Optional.of(LocalDate.of(2024, 1, 1)));
        when(repository.findMaxReportDate("user-1"))
                .thenReturn(Optional.of(LocalDate.of(2024, 1, 7)));

        assertThrows(ReportNotReadyException.class, () -> service.getReport("user-1", from, to));
    }

    @Test
    void getReport_throwsWhenNoDataAtAll() {
        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 7);
        when(repository.findReportRows(anyString(), any(), any())).thenReturn(List.of());
        when(repository.findMinReportDate("user-1")).thenReturn(Optional.empty());

        assertThrows(ReportNotReadyException.class, () -> service.getReport("user-1", from, to));
    }

    @Test
    void getReport_throwsWhenPeriodInvalid() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getReport("user-1",
                        LocalDate.of(2024, 1, 7), LocalDate.of(2024, 1, 1)));
    }
}