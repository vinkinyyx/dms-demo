package com.dms.common.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * TOTP (RFC 6238) helper: generate/verify time-based one-time passwords.
 * HMAC-SHA1, 6 digits, 30s window. No external dependencies.
 */
public final class TotpUtil {

    private static final int DIGITS = 6;
    private static final int PERIOD = 30;
    private static final int DRIFT_WINDOWS = 1;
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpUtil() {
    }

    public static String generateSecret() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public static String currentCode(String base32Secret) {
        return codeAt(base32Secret, System.currentTimeMillis() / 1000L);
    }

    public static String codeAt(String base32Secret, long epochSeconds) {
        long counter = epochSeconds / PERIOD;
        byte[] key = base32Decode(base32Secret);
        byte[] data = new byte[8];
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (counter & 0xff);
            counter >>= 8;
        }
        byte[] hash = hmacSha1(key, data);
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int otp = binary % (int) Math.pow(10, DIGITS);
        return String.format("%0" + DIGITS + "d", otp);
    }

    public static boolean verify(String base32Secret, String submitted) {
        if (submitted == null || !submitted.matches("\\d{6}")) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000L;
        for (int i = -DRIFT_WINDOWS; i <= DRIFT_WINDOWS; i++) {
            String expected = codeAt(base32Secret, now + (long) i * PERIOD);
            if (constantTimeEquals(expected, submitted)) {
                return true;
            }
        }
        return false;
    }

    public static String otpAuthUrl(String issuer, String account, String base32Secret) {
        String label = URLEncoder.encode(issuer + ":" + account, StandardCharsets.UTF_8).replace("+", "%20");
        String params = "?secret=" + base32Secret
                + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8).replace("+", "%20")
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD;
        return "otpauth://totp/" + label + params;
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA1 unavailable", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1f;
                bitsLeft -= 5;
                sb.append(BASE32_ALPHABET.charAt(index));
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1f;
            sb.append(BASE32_ALPHABET.charAt(index));
        }
        while (sb.length() % 8 != 0) {
            sb.append('=');
        }
        return sb.toString();
    }

    static byte[] base32Decode(String input) {
        String cleaned = input.replace("=", "").toUpperCase();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : cleaned.toCharArray()) {
            int val = BASE32_ALPHABET.indexOf(c);
            if (val < 0) {
                continue;
            }
            buffer = (buffer << 5) | val;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }
}
