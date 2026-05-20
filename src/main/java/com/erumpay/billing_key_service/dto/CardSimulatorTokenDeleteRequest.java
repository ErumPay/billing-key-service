package com.erumpay.billing_key_service.dto;

import com.erumpay.billing_key_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CardSimulatorTokenDeleteRequest(
        @JsonProperty("pg_id") String pgId,
        @JsonProperty("card_company") CardCompany cardCompany,
        @JsonProperty("card_token") String cardToken
) {
    @Override
    public String toString() {
        return "CardSimulatorTokenDeleteRequest(pgId=%s, cardCompany=%s, cardToken=****)"
                .formatted(pgId, cardCompany);
    }
}
