package com.erumpay.billing_key_service.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

// AES-256-GCM 인증 암호화. 메시지당 IV(12바이트) 자동 생성 후 암호문 앞에 동봉.
@Component
public class AesCryptoUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${aes.secret-key}")
    private String secretKey;

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, buildKeySpec(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv)
                    .put(encrypted)
                    .array();
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new RuntimeException("AES 암호화 실패", e);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_LENGTH_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, buildKeySpec(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 복호화 실패", e);
        }
    }

    // Base64로 인코딩된 32바이트 키만 허용
    private SecretKeySpec buildKeySpec() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKey);
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("aes.secret-key must decode to exactly 32 bytes");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
