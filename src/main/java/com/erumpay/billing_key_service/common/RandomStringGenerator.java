package com.erumpay.billing_key_service.common;

import java.security.SecureRandom;

// SecureRandom 기반 hex 랜덤 문자열 생성. 멱등성 키 RANDOM 부분, 빌링키 등에 공통 사용.
public class RandomStringGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private RandomStringGenerator() {}

    public static String generateHex(int length) {
        if (length < 1) {
            throw new IllegalArgumentException("length must be positive");
        }
        char[] result = new char[length];
        for (int i = 0; i < length; i++) {
            result[i] = HEX_CHARS[RANDOM.nextInt(16)];
        }
        return new String(result);
    }
}
