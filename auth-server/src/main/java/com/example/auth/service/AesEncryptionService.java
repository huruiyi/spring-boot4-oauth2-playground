package com.example.auth.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Service
public class AesEncryptionService {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 128;

  private final SecretKey secretKey;
  private final SecureRandom secureRandom = new SecureRandom();

  public AesEncryptionService(SecretKey aesSecretKey) {
    this.secretKey = aesSecretKey;
  }

  public String encrypt(String plainText) {
    if (plainText == null || plainText.isEmpty()) {
      return null;
    }
    try {
      byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

      byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[iv.length + cipherText.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);

      return Base64.getEncoder().encodeToString(combined);
    } catch (Exception e) {
      log.error("加密失败: {}", e.getMessage(), e);
      throw new RuntimeException("加密失败", e);
    }
  }

  public String decrypt(String encryptedText) {
    if (encryptedText == null || encryptedText.isEmpty()) {
      return null;
    }
    try {
      byte[] combined = Base64.getDecoder().decode(encryptedText);

      byte[] iv = new byte[GCM_IV_LENGTH];
      System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH);

      byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH];
      System.arraycopy(combined, GCM_IV_LENGTH, cipherText, 0, cipherText.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

      byte[] plainText = cipher.doFinal(cipherText);
      return new String(plainText, StandardCharsets.UTF_8);
    } catch (Exception e) {
      log.error("解密失败: {}", e.getMessage(), e);
      throw new RuntimeException("解密失败", e);
    }
  }
}
