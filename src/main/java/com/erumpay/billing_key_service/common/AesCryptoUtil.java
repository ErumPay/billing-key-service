package com.erumpay.billing_key_service.common;

import com.erumpay.billing_key_service.exception.CustomException;
import com.erumpay.billing_key_service.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class AesCryptoUtil {

    private static final String ALGORITHM = "AES/ECB/PKCS5Padding";

    @Value("${aes.secret-key}")
    private String secretKey;

    private SecretKeySpec keySpec;

    @PostConstruct
    void init() {
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException("aes.secret-key must be 16, 24, or 32 bytes (UTF-8)");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        this.secretKey = null;
    }

    public String encrypt(String plaintext) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.ENCRYPTION_ERROR, e);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] decoded = Base64.getDecoder().decode(ciphertext);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new CustomException(ErrorCode.ENCRYPTION_ERROR, e);
        }
    }
}
