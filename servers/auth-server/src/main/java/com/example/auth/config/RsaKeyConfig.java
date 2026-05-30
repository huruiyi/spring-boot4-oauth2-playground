package com.example.auth.config;

import com.nimbusds.jose.jwk.RSAKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Configuration
@ConfigurationProperties(prefix = "rsa-key")
public class RsaKeyConfig {

  private static final Logger log = LoggerFactory.getLogger(RsaKeyConfig.class);

  private String file = "./config/rsa-key.json";

  public String getFile() {
    return file;
  }

  public void setFile(String file) {
    this.file = file;
  }

  @Bean
  public RSAKey rsaKey() {
    Path path = Paths.get(file);

    if (Files.exists(path)) {
      try {
        String json = Files.readString(path);
        RSAKey key = RSAKey.parse(json);
        log.info("RSA key loaded from: {}", path.toAbsolutePath());
        return key;
      } catch (Exception e) {
        log.warn("Failed to load RSA key from {}, generating new key", path, e);
      }
    }

    RSAKey key = generateRsaKey();

    try {
      Path parent = path.getParent();
      if (parent != null && !Files.exists(parent)) {
        Files.createDirectories(parent);
      }
      Files.writeString(path, key.toJSONString());
      log.info("RSA key saved to: {}", path.toAbsolutePath());
    } catch (IOException e) {
      log.error("Failed to save RSA key to {}", path, e);
    }

    return key;
  }

  private static RSAKey generateRsaKey() {
    try {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      KeyPair kp = gen.generateKeyPair();
      return new RSAKey.Builder((RSAPublicKey) kp.getPublic())
          .privateKey((RSAPrivateKey) kp.getPrivate())
          .keyID(UUID.randomUUID().toString())
          .build();
    } catch (Exception e) {
      throw new IllegalStateException("Failed to generate RSA key", e);
    }
  }
}
