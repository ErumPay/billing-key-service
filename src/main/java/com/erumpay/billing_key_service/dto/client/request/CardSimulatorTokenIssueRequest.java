package com.erumpay.billing_key_service.dto.client.request;

import com.erumpay.billing_key_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// [be] 하지혁 260603 카드사 토큰 발급 Client Request DTO
public record CardSimulatorTokenIssueRequest(
    @JsonProperty("pg_id") @NotBlank String pgId,
    @JsonProperty("card_company") @NotNull CardCompany cardCompany,
    @JsonProperty("card_number") @NotBlank @Pattern(regexp = "^\\d{16}$") String cardNumber,
    @JsonProperty("expiry_date") @NotBlank @Pattern(regexp = "^\\d{2}(0[1-9]|1[0-2])$") String expiryDate,
    @NotBlank @Pattern(regexp = "^\\d{3}$") String cvc,
    @JsonProperty("password_2digit") @NotBlank @Pattern(regexp = "^\\d{2}$") String password2digit,
    @JsonProperty("birth_date") @NotBlank @Pattern(regexp = "^\\d{6}$") String birthDate
) {
    @Override
    public String toString() {
        return "CardSimulatorTokenIssueRequest(pgId=%s, cardCompany=%s, cardNumber=****, expiryDate=****, cvc=****, password2digit=****, birthDate=****)"
            .formatted(pgId, cardCompany);
    }
}
