package protrader.funds.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import protrader.funds.dto.ApiError;
import protrader.funds.services.TransferIdReusedException;
import protrader.funds.utils.ApiException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> api(ApiException e) {
        return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage(), e.field(), null));
    }

    @ExceptionHandler(TransferIdReusedException.class)
    ResponseEntity<ApiError> reused(TransferIdReusedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new ApiError("transfer_id_reused",
                "this transfer_id was already used for a different transfer", "transfer_id", null));
    }

    /** Body isn't JSON, is broken JSON, or has unknown fields. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(ApiError.of("invalid_json", "request body is not valid JSON for this endpoint"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> mediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiError.of("content_type_must_be_json", "Content-Type must be application/json"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        // Spring's own 4xx (no handler, wrong method, bad parameter type, ...): keep
        // the status, drop the detail
        if (e instanceof ErrorResponse er && er.getStatusCode().is4xxClientError()) {
            return ResponseEntity.status(er.getStatusCode())
                    .body(ApiError.forStatus(er.getStatusCode().value(), "request not accepted"));
        }
        log.error("unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of("internal", "internal error"));
    }
}
