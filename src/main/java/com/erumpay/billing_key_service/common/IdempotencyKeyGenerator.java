package com.erumpay.billing_key_service.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// 형식: {PG번호(3)}-{OP(3)}-{TIMESTAMP(14)}-{RANDOM(25)} = 48자
@Component
public class IdempotencyKeyGenerator {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final int RANDOM_LENGTH = 25;

    @Value("${pg.id}")
    private String pgId;

    public String generate(Operation operation) {
        return String.format("%s-%s-%s-%s",
                pgId,
                operation.name(),
                LocalDateTime.now().format(TIMESTAMP_FORMATTER),
                RandomStringGenerator.generateHex(RANDOM_LENGTH));
    }

    public enum Operation {
        ISS, DEL
    }
}
