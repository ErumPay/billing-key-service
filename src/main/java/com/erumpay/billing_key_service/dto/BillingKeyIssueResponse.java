package com.erumpay.billing_key_service.dto;

import com.erumpay.billing_key_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record BillingKeyIssueResponse(
        @JsonProperty("pay_card_id") Long payCardId,
        @JsonProperty("billing_key") String billingKey,
        @JsonProperty("masked_number") String maskedNumber,
        @JsonProperty("card_company") CardCompany cardCompany,
        @JsonProperty("response_code") String responseCode,
        @JsonProperty("response_message") String responseMessage
) {
}
