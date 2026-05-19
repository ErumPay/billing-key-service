package com.erumpay.billing_key_service.controller;

import com.erumpay.billing_key_service.dto.BillingKeyIssueRequest;
import com.erumpay.billing_key_service.dto.BillingKeyIssueResponse;
import com.erumpay.billing_key_service.dto.BillingKeyTokenRetrieveRequest;
import com.erumpay.billing_key_service.dto.BillingKeyTokenRetrieveResponse;
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

    @PostMapping("/issue")
    public BillingKeyIssueResponse issue(@RequestBody @Valid BillingKeyIssueRequest request) {
        return billingKeyService.issue(request);
    }

    @PostMapping("/token-retrieve")
    public BillingKeyTokenRetrieveResponse tokenRetrieve(@RequestBody @Valid BillingKeyTokenRetrieveRequest request) {
        return billingKeyService.tokenRetrieve(request);
    }
}
