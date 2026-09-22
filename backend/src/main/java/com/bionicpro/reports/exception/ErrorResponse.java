package com.bionicpro.reports.exception;

/**
 * Единый формат ошибок API.
 */
public record ErrorResponse(String error, String message) {
}