package com.erumpay.billing_key_service.exception;

import feign.FeignException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    protected ResponseEntity<ErrorResponse> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.error("handleMessageNotReadable : {}", e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.MESSAGE_NOT_READABLE);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MethodValidationException.class,
            HandlerMethodValidationException.class
    })
    protected ResponseEntity<ErrorResponse> handleValidationException(Exception e) {
        log.error("handleValidationException [{}] : {}", e.getClass().getSimpleName(), e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.INVALID_REQUEST);
    }

    @ExceptionHandler(DataAccessException.class)
    protected ResponseEntity<ErrorResponse> handleDatabaseException(DataAccessException e) {
        log.error("handleDatabaseException : {}", e.getMessage(), e);
        return ErrorResponse.toResponseEntity(ErrorCode.DATABASE_ERROR);
    }

    @ExceptionHandler(CustomException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        log.error("handleCustomException : {}", e.getErrorCode());
        return ErrorResponse.toResponseEntity(e.getErrorCode());
    }

    // 카드시뮬 응답 4xx: 우리 요청 빌드 버그 → BIL-CORE-504. RetryableException은 별도 fallback(reconcile) 흐름이므로 여기 도달 안 함.
    @ExceptionHandler(FeignException.class)
    protected ResponseEntity<ErrorResponse> handleFeignException(FeignException e) {
        int status = e.status();
        if (status >= 400 && status < 500) {
            log.error("카드시뮬 4xx 응답 status={} : {}", status, e.getMessage());
            return ErrorResponse.toResponseEntity(ErrorCode.CARD_SIMULATOR_BAD_REQUEST);
        }
        log.error("카드시뮬 5xx/기타 status={} : {}", status, e.getMessage());
        return ErrorResponse.toResponseEntity(ErrorCode.CARD_SIMULATOR_UNAVAILABLE);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        log.error("handleException : {}", e.getMessage(), e);
        return ErrorResponse.toResponseEntity(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
