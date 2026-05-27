package com.erumpay.billing_key_service.client;

import com.erumpay.billing_key_service.dto.client.request.CardSimulatorTokenDeleteRequest;
import com.erumpay.billing_key_service.dto.client.request.CardSimulatorTokenInquireRequest;
import com.erumpay.billing_key_service.dto.client.request.CardSimulatorTokenIssueRequest;
import com.erumpay.billing_key_service.dto.client.response.CardSimulatorTokenDeleteResponse;
import com.erumpay.billing_key_service.dto.client.response.CardSimulatorTokenResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "card-simulator", url = "${card-simulator.base-url}")
public interface CardSimulatorClient {

    /* ***** API 1 : Card Token Issue ***** */
    @PostMapping("/api/v1/card-simulator/token/issue")
    CardSimulatorTokenResponse issueToken(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @RequestBody CardSimulatorTokenIssueRequest request);

    /* ***** API 2 : Card Token Delete ***** */
    @PostMapping("/api/v1/card-simulator/token/delete")
    CardSimulatorTokenDeleteResponse deleteToken(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @RequestBody CardSimulatorTokenDeleteRequest request);

                                                 /* ***** API 3 : Card Token Inquire ***** */
    @PostMapping("/api/v1/card-simulator/token/inquire")
    CardSimulatorTokenResponse inquireToken(@RequestBody CardSimulatorTokenInquireRequest request);

}
