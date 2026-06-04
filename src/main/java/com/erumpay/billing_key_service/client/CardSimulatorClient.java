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

    // [be] 하지혁 260603 CardSimulator Client API 1 : 카드사 토큰 발급
    @PostMapping("/api/v1/card-simulator/token/issue")
    CardSimulatorTokenResponse issueToken(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @RequestBody CardSimulatorTokenIssueRequest request);

    // [be] 하지혁 260603 CardSimulator Client API 2 : 카드사 토큰 삭제
    @PostMapping("/api/v1/card-simulator/token/delete")
    CardSimulatorTokenDeleteResponse deleteToken(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                 @RequestBody CardSimulatorTokenDeleteRequest request);

    // [be] 하지혁 260603 CardSimulator Client API 3 : 카드사 토큰 조회
    @PostMapping("/api/v1/card-simulator/token/inquire")
    CardSimulatorTokenResponse inquireToken(@RequestBody CardSimulatorTokenInquireRequest request);

}
