package com.erumpay.billing_key_service.dto.api.response;

import com.erumpay.billing_key_service.common.CardCompany;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record BillingKeyTokenRetrieveResponse(
        @JsonProperty("billing_key") String billingKey,
        @JsonProperty("card_token") String cardToken,
        @JsonProperty("card_company") CardCompany cardCompany
) {
}
