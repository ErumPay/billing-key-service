package com.erumpay.billing_key_service.dto.client.response;

import com.erumpay.billing_key_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;

// [be] 하지혁 260603 카드사 토큰 발급/조회 Client Response DTO
public record CardSimulatorTokenResponse(
        @JsonProperty("pg_id") String pgId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("token_status") String tokenStatus,
        @JsonProperty("card_token") String cardToken,
        @JsonProperty("card_company") CardCompany cardCompany,
        @JsonProperty("masked_number") String maskedNumber,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_message") String responseMessage
) {
}
