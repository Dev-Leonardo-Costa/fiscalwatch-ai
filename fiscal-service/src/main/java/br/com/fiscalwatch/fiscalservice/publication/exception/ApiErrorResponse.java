package br.com.fiscalwatch.fiscalservice.publication.exception;

import java.time.LocalDateTime;

public record ApiErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message
) {
}