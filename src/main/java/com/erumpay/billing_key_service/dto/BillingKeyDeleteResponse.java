package com.erumpay.billing_key_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record BillingKeyDeleteResponse(
        @JsonProperty("pay_card_id") Long payCardId,
        @JsonProperty("billing_key") String billingKey,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_message") String responseMessage
) {
}
