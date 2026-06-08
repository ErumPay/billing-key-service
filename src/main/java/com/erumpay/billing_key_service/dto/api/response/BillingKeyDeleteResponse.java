package com.erumpay.billing_key_service.dto.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

// [be] 하지혁 260603 빌링키 삭제 Response DTO
@Builder
public record BillingKeyDeleteResponse(
        @JsonProperty("pay_card_id") Long payCardId,
        @JsonProperty("billing_key") String billingKey,
        @JsonProperty("response_http") Integer responseHttp,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_reason") String responseReason,
        @JsonProperty("response_message") String responseMessage
) {
}
