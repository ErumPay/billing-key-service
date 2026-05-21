package com.erumpay.billing_key_service.dto.api.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record BillingKeyDeleteRequest(
        @JsonProperty("pay_card_id") @NotNull @Positive Long payCardId,
        @JsonProperty("billing_key") @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{32}$") String billingKey
) {
}
