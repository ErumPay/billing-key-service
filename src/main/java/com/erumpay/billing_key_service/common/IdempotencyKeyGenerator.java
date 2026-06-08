package com.erumpay.billing_key_service.common;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

// 형식: {PG번호(3)}-{OP(3)}-{TIMESTAMP(14)}-{RANDOM(25)} = 48자
@Component
public class IdempotencyKeyGenerator {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int RANDOM_LENGTH = 25;
    private static final Pattern PG_ID_PATTERN = Pattern.compile("^[A-Z0-9]{3}$");

    @Value("${pg.id}")
    private String pgId;

    @PostConstruct
    void validatePgId() {
        if (pgId == null || !PG_ID_PATTERN.matcher(pgId).matches()) {
            throw new IllegalStateException("pg.id must be exactly 3 uppercase alphanumeric characters, got: " + pgId);
        }
    }

    public String generate(Operation operation) {
        return String.format("%s-%s-%s-%s",
                pgId,
                operation.name(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER),
                RandomStringGenerator.generateHex(RANDOM_LENGTH));
    }

    public enum Operation {
        ISS,    // 발행
        DEL,    // 삭제
        PAY,    // 결제
        CNL,    // 결제 취소
        PRE,    // 가승인
        PRC,    // 가승인 취소
    }
}
