package com.bionicpro.reports.exception;

/**
 * Исключение: данные за запрошенный период ещё не обработаны ETL-процессом
 * и отсутствуют в OLAP-витрине.
 */
public class ReportNotReadyException extends RuntimeException {

    public ReportNotReadyException(String message) {
        super(message);
    }
}