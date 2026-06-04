package com.erumpay.billing_key_service.dto.client.request;

import com.erumpay.billing_key_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;

// [be] 하지혁 260603 카드사 토큰 삭제 Client Request DTO
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
