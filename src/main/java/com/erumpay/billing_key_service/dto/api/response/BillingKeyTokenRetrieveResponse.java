package com.erumpay.billing_key_service.dto.api.response;

import com.erumpay.billing_key_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

// [be] 하지혁 260603 빌링키 토큰 조회 Response DTO
@Builder
public record BillingKeyTokenRetrieveResponse(
        @JsonProperty("billing_key") String billingKey,
        @JsonProperty("card_token") String cardToken,
        @JsonProperty("card_company") CardCompany cardCompany,
        @JsonProperty("response_http") Integer responseHttp,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_reason") String responseReason,
        @JsonProperty("response_message") String responseMessage
) {
}
