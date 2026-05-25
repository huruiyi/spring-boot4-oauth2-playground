package com.example.auth.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import com.warrenstrange.googleauth.GoogleAuthenticatorQRGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
public class MfaService {

    private final GoogleAuthenticator googleAuthenticator = new GoogleAuthenticator();
    private final SecureRandom secureRandom = new SecureRandom();
    
    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_LENGTH = 8;

    public String generateSecret() {
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        return key.getKey();
    }

    public String generateQrCodeBase64(String username, String secret, String issuer) {
        try {
            String otpAuthUrl = GoogleAuthenticatorQRGenerator.getOtpAuthTotpURL(issuer, username, 
                new GoogleAuthenticatorKey.Builder(secret).build());
            
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpAuthUrl, BarcodeFormat.QR_CODE, 300, 300);
            
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            byte[] qrCodeBytes = outputStream.toByteArray();
            
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(qrCodeBytes);
        } catch (Exception e) {
            log.error("生成 QR 码失败: {}", e.getMessage(), e);
            throw new RuntimeException("生成 QR 码失败", e);
        }
    }

    public boolean verifyCode(String secret, int code) {
        return googleAuthenticator.authorize(secret, code);
    }

    public boolean isCodeValid(String secret, String codeStr) {
        if (secret == null || codeStr == null || codeStr.length() != 6) {
            return false;
        }
        try {
            int code = Integer.parseInt(codeStr);
            return verifyCode(secret, code);
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    public List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            codes.add(generateSingleRecoveryCode());
        }
        return codes;
    }
    
    private String generateSingleRecoveryCode() {
        StringBuilder code = new StringBuilder();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < RECOVERY_CODE_LENGTH; i++) {
            code.append(chars.charAt(secureRandom.nextInt(chars.length())));
        }
        return code.toString();
    }
    
    public String serializeRecoveryCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < codes.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(codes.get(i)).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
    
    public List<String> deserializeRecoveryCodes(String json) {
        List<String> codes = new ArrayList<>();
        if (json == null || json.isEmpty() || json.equals("[]")) {
            return codes;
        }
        try {
            String content = json.substring(1, json.length() - 1);
            if (content.isEmpty()) {
                return codes;
            }
            String[] parts = content.split(",");
            for (String part : parts) {
                String code = part.trim().replace("\"", "");
                if (!code.isEmpty()) {
                    codes.add(code);
                }
            }
        } catch (Exception e) {
            log.error("反序列化恢复码失败: {}", e.getMessage(), e);
        }
        return codes;
    }
    
    public String consumeRecoveryCode(String recoveryCodesJson, String inputCode) {
        if (recoveryCodesJson == null || inputCode == null) {
            return null;
        }
        
        List<String> codes = deserializeRecoveryCodes(recoveryCodesJson);
        String normalizedInput = inputCode.toUpperCase().trim();
        
        if (codes.contains(normalizedInput)) {
            codes.remove(normalizedInput);
            log.info("恢复码已使用，剩余 {} 个", codes.size());
            return serializeRecoveryCodes(codes);
        }
        
        return null;
    }
    
    public boolean isRecoveryCode(String recoveryCodesJson, String inputCode) {
        if (recoveryCodesJson == null || inputCode == null) {
            return false;
        }
        List<String> codes = deserializeRecoveryCodes(recoveryCodesJson);
        return codes.contains(inputCode.toUpperCase().trim());
    }
}

