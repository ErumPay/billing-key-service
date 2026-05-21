package com.erumpay.billing_key_service.dto.client.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CardSimulatorTokenDeleteResponse(
        @JsonProperty("pg_id") String pgId,
        @JsonProperty("idempotency_key") String idempotencyKey,
        @JsonProperty("card_token") String cardToken,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_message") String responseMessage
) {
}
