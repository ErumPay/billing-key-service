package com.erumpay.billing_key_service.dto;

import com.erumpay.billing_key_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CardSimulatorTokenIssueRequest(
        @JsonProperty("pg_id") String pgId,
        @JsonProperty("card_company") CardCompany cardCompany,
        @JsonProperty("card_number") String cardNumber,
        @JsonProperty("expiry_date") String expiryDate,
        String cvc,
        @JsonProperty("password_2digit") String password2digit,
        @JsonProperty("birth_date") String birthDate
) {
}
