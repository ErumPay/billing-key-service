package com.erumpay.billing_key_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CardSimulatorTokenInquireRequest(
        @JsonProperty("target_idempotency_key") String targetIdempotencyKey
) {
}
