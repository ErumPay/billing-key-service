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

    // [be] 하지혁 260603 BillingKey API 1 : 빌링키 발급
    @PostMapping("/issue")
    public BillingKeyIssueResponse issue(@RequestBody @Valid BillingKeyIssueRequest request) {
        return billingKeyService.issue(request);
    }

    // [be] 하지혁 260603 BillingKey API 2 : 빌링키 삭제
    @PostMapping("/delete")
    public BillingKeyDeleteResponse delete(@RequestBody @Valid BillingKeyDeleteRequest request) {
        return billingKeyService.delete(request);
    }

    // [be] 하지혁 260603 BillingKey API 3 : 빌링키 토큰 조회
    @PostMapping("/token-retrieve")
    public BillingKeyTokenRetrieveResponse tokenRetrieve(@RequestBody @Valid BillingKeyTokenRetrieveRequest request) {
        return billingKeyService.tokenRetrieve(request);
    }

}
