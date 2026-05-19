package com.erumpay.billing_key_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record BillingKeyIssueRequest(
        @JsonProperty("pay_card_id") @NotNull Long payCardId,
        @JsonProperty("card_number") @NotBlank @Pattern(regexp = "^\\d{14,19}$") String cardNumber,
        @JsonProperty("expiry_date") @NotBlank @Pattern(regexp = "^\\d{2}(0[1-9]|1[0-2])$") String expiryDate,
        @NotBlank @Pattern(regexp = "^\\d{3,4}$") String cvc,
        @JsonProperty("password_2digit") @NotBlank @Pattern(regexp = "^\\d{2}$") String password2digit,
        @JsonProperty("birth_date") @NotBlank @Pattern(regexp = "^\\d{6}(\\d{2})?$") String birthDate
) {
    @Override
    public String toString() {
        return "BillingKeyIssueRequest(payCardId=%s, cardNumber=****, expiryDate=****, cvc=****, password2digit=****, birthDate=****)"
                .formatted(payCardId);
    }
}
