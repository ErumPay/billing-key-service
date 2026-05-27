package com.erumpay.billing_key_service.controller;

import com.erumpay.billing_key_service.dto.api.request.BillingKeyDeleteRequest;
import com.erumpay.billing_key_service.dto.api.request.BillingKeyIssueRequest;
import com.erumpay.billing_key_service.dto.api.request.BillingKeyTokenRetrieveRequest;
import com.erumpay.billing_key_service.dto.api.response.BillingKeyDeleteResponse;
import com.erumpay.billing_key_service.dto.api.response.BillingKeyIssueResponse;
import com.erumpay.billing_key_service.dto.api.response.BillingKeyTokenRetrieveResponse;
import com.erumpay.billing_key_service.service.BillingKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing-key")
@RequiredArgsConstructor
public class BillingKeyController {

    private final BillingKeyService billingKeyService;

    /* ***** API 1 : Billing Key Issue ***** */
    @PostMapping("/issue")
    public BillingKeyIssueResponse issue(@RequestBody @Valid BillingKeyIssueRequest request) {
        return billingKeyService.issue(request);
    }

    /* ***** API 2 : Billing Key Delete ***** */
    @PostMapping("/delete")
    public BillingKeyDeleteResponse delete(@RequestBody @Valid BillingKeyDeleteRequest request) {
        return billingKeyService.delete(request);
    }

    /* ***** API 3 : Billing Key TokenRetrieve ***** */
    @PostMapping("/token-retrieve")
    public BillingKeyTokenRetrieveResponse tokenRetrieve(@RequestBody @Valid BillingKeyTokenRetrieveRequest request) {
        return billingKeyService.tokenRetrieve(request);
    }

}
