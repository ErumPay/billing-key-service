package com.erumpay.billing_key_service.exception;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "BIL-CORE-001", "BILLING_INVALID_REQUEST", "잘못된 요청입니다."),
    MESSAGE_NOT_READABLE(HttpStatus.BAD_REQUEST, "BIL-CORE-003", "BILLING_MESSAGE_NOT_READABLE", "요청 본문을 읽을 수 없습니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "BIL-CORE-900", "BILLING_INTERNAL_ERROR", "빌링키 서비스 내부 오류가 발생했습니다."),
    ENCRYPTION_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "BIL-CORE-901", "BILLING_ENCRYPTION_ERROR", "암복호화 처리에 실패했습니다."),
    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "BIL-CORE-902", "BILLING_DATABASE_ERROR", "데이터베이스 처리에 실패했습니다."),
    CARD_SIMULATOR_BAD_REQUEST(HttpStatus.INTERNAL_SERVER_ERROR, "BIL-CORE-504", "BILLING_CARD_SIMULATOR_BAD_REQUEST", "카드사 요청 처리 실패."),
    CARD_SIMULATOR_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "BIL-CORE-503", "BILLING_CARD_SIMULATOR_UNAVAILABLE", "카드사 서비스를 일시적으로 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String reason;
    private final String message;
}
