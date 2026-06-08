package com.erumpay.billing_key_service.dto.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

// [be] 하지혁 260603 빌링키 토큰 조회 Request DTO
public record BillingKeyTokenRetrieveRequest(
        @JsonProperty("billing_key") @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{32}$") String billingKey
) {
}
