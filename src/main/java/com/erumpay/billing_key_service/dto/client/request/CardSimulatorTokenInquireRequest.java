package com.erumpay.billing_key_service.dto.client.request;

import com.fasterxml.jackson.annotation.JsonProperty;

// [be] 하지혁 260603 카드사 토큰 조회 Client Request DTO
public record CardSimulatorTokenInquireRequest(
        @JsonProperty("target_idempotency_key") String targetIdempotencyKey
) {
}
