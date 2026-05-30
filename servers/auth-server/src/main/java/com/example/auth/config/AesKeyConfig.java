package com.example.auth.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;

@Configuration
@ConfigurationProperties(prefix = "aes-key")
public class AesKeyConfig {

  private static final Logger log = LoggerFactory.getLogger(AesKeyConfig.class);

  private String file = System.getProperty("user.home") + "/.config/spring-boot4-oauth2/aes-key.key";

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
  }

  @Bean
  public SecretKey aesSecretKey() {
    Path path = Paths.get(file);

    if (Files.exists(path)) {
      try {
        String base64Key = Files.readString(path).trim();
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
        log.info("AES key loaded from: {}", path.toAbsolutePath());
        return keySpec;
      } catch (Exception e) {
        log.warn("Failed to load AES key from {}, generating new key", path, e);
      }
    }

    SecretKey key = generateAesKey();

    try {
      Path parent = path.getParent();
      if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent);
      }
      String base64Key = Base64.getEncoder().encodeToString(key.getEncoded());
      Files.writeString(path, base64Key);
      log.info("AES key saved to: {}", path.toAbsolutePath());
    } catch (IOException e) {
      log.error("Failed to save AES key to {}", path, e);
    }

    return key;
  }

  private static SecretKey generateAesKey() {
    try {
      KeyGenerator gen = KeyGenerator.getInstance("AES");
      gen.init(256);
      return gen.generateKey();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to generate AES key", e);
    }
  }
}
