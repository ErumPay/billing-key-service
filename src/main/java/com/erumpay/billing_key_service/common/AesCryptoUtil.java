package com.erumpay.billing_key_service.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

// AES-256-ECB 암복호화. ECB는 동일 평문 블록 → 동일 암호문 약점 존재. 학습 목적 단순화 버전.
@Component
public class AesCryptoUtil {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    @Value("${aes.secret-key}")
    private String secretKey;

    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, buildKeySpec());
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("AES 암호화 실패", e);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, buildKeySpec());
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("AES 복호화 실패", e);
        }
    }

    // 설정 키 길이와 무관하게 SHA-256으로 32바이트 키 도출
    private SecretKeySpec buildKeySpec() throws Exception {
        byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                .digest(secretKey.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
