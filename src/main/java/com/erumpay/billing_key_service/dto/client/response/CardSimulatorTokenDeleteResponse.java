package com.erumpay.billing_key_service.dto.client.response;

import com.fasterxml.jackson.annotation.JsonProperty;

// [be] 하지혁 260603 카드사 토큰 삭제 Client Response DTO
public record CardSimulatorTokenDeleteResponse(
        @JsonProperty("pg_id") String pgId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("card_token") String cardToken,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_message") String responseMessage
) {
}
