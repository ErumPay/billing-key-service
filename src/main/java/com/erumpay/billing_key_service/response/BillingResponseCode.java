package com.erumpay.billing_key_service.response;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

// [be] 하지혁 260603 빌링키 응답 코드 enum
@Getter
@AllArgsConstructor
public enum BillingResponseCode {

    // ===== KEY (빌링키 자체) =====
    BILLING_KEY_SUCCESS(HttpStatus.OK, "BIL-KEY-100", "BILLING_KEY_SUCCESS", "정상 처리되었습니다."),
    BILLING_KEY_ALREADY_ACTIVE(HttpStatus.OK, "BIL-KEY-101", "BILLING_KEY_ALREADY_ACTIVE", "이미 발급된 빌링키입니다."),
    BILLING_KEY_PENDING(HttpStatus.OK, "BIL-KEY-102", "BILLING_KEY_PENDING", "빌링키 발급 처리 중입니다."),
    BILLING_KEY_NOT_FOUND(HttpStatus.OK, "BIL-KEY-103", "BILLING_KEY_NOT_FOUND", "빌링키를 찾을 수 없습니다."),
    BILLING_KEY_ISSUE_FAILED(HttpStatus.OK, "BIL-KEY-104", "BILLING_KEY_ISSUE_FAILED", "빌링키 발급에 실패했습니다."),
    BILLING_KEY_ALREADY_DELETED(HttpStatus.OK, "BIL-KEY-105", "BILLING_KEY_ALREADY_DELETED", "이미 삭제된 빌링키입니다."),

    // ===== CARD (카드시뮬 CARD 도메인 1:1 매핑) =====
    CARD_LOST(HttpStatus.OK, "BIL-CARD-201", "CARD_LOST", "분실 신고된 카드입니다."),
    CARD_EXPIRED(HttpStatus.OK, "BIL-CARD-202", "CARD_EXPIRED", "만료된 카드입니다."),
    CARD_DELETED(HttpStatus.OK, "BIL-CARD-203", "CARD_DELETED", "해지된 카드입니다."),
    CARD_INVALID_PASSWORD(HttpStatus.OK, "BIL-CARD-205", "CARD_INVALID_PASSWORD", "비밀번호가 일치하지 않습니다."),
    CARD_NOT_FOUND(HttpStatus.OK, "BIL-CARD-206", "CARD_NOT_FOUND", "존재하지 않는 카드입니다."),
    CARD_INVALID_EXPIRY(HttpStatus.OK, "BIL-CARD-207", "CARD_INVALID_EXPIRY", "카드 유효기간이 일치하지 않습니다."),
    CARD_INVALID_CVC(HttpStatus.OK, "BIL-CARD-208", "CARD_INVALID_CVC", "CVC가 일치하지 않습니다."),

    // ===== TOKEN (카드시뮬 TOKEN 도메인 1:1 매핑, 삭제 시점) =====
    TOKEN_NOT_FOUND(HttpStatus.OK, "BIL-TOKEN-101", "TOKEN_NOT_FOUND", "카드사 토큰을 찾을 수 없습니다."),

    // ===== USER (카드시뮬 USER 도메인 1:1 매핑) =====
    USER_BIRTH_INVALID(HttpStatus.OK, "BIL-USER-501", "USER_BIRTH_INVALID", "본인 정보가 일치하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String reason;
    private final String message;

    public int getHttp() {
        return status.value();
    }

    // 카드시뮬 응답코드(SIM-xxx-NNN) → 빌링키 응답코드 1:1 매핑
    public static BillingResponseCode fromSimulatorCode(String simulatorCode) {
        if (simulatorCode == null) return null;
        return switch (simulatorCode) {
            case "SIM-TOKEN-100" -> BILLING_KEY_SUCCESS;
            case "SIM-TOKEN-101" -> TOKEN_NOT_FOUND;
            case "SIM-TOKEN-102" -> BILLING_KEY_ALREADY_ACTIVE;
            case "SIM-TOKEN-103" -> BILLING_KEY_ALREADY_DELETED;
            case "SIM-TOKEN-104" -> BILLING_KEY_ISSUE_FAILED;
            case "SIM-CARD-201" -> CARD_LOST;
            case "SIM-CARD-202" -> CARD_EXPIRED;
            case "SIM-CARD-203" -> CARD_DELETED;
            case "SIM-CARD-205" -> CARD_INVALID_PASSWORD;
            case "SIM-CARD-206" -> CARD_NOT_FOUND;
            case "SIM-CARD-207" -> CARD_INVALID_EXPIRY;
            case "SIM-CARD-208" -> CARD_INVALID_CVC;
            case "SIM-USER-501" -> USER_BIRTH_INVALID;
            default -> null;
        };
    }
}
