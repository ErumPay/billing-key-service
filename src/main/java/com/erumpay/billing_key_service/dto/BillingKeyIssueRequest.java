package com.erumpay.billing_key_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record BillingKeyIssueRequest(
        @JsonProperty("pay_card_id") @NotNull Long payCardId,
        @JsonProperty("card_number") @NotBlank String cardNumber,
        @JsonProperty("expiry_date") @NotBlank String expiryDate,
        @NotBlank String cvc,
        @JsonProperty("password_2digit") @NotBlank String password2digit,
        @JsonProperty("birth_date") @NotBlank String birthDate
) {
}
