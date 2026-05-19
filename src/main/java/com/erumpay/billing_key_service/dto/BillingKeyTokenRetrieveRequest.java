package com.erumpay.billing_key_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BillingKeyTokenRetrieveRequest(
        @JsonProperty("billing_key") @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{32}$") String billingKey
) {
}
