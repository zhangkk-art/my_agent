package com.myagent.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Volcengine IAM v4 HMAC-SHA256 signer for visual API (cv service).
 *
 * Volcengine signing spec:
 *   kSecret  = SecretAccessKey (raw bytes)
 *   kDate    = HMAC(kSecret,  YYYYMMDD)
 *   kRegion  = HMAC(kDate,    region)
 *   kService = HMAC(kRegion,  service)
 *   kSigning = HMAC(kService, "request")
 *
 * Note: No prefix (like AWS4 or HMAC) is appended to the secret key.
 */
public class IamV4Signer {

    private static final String ALGORITHM = "HMAC-SHA256";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final String accessKey;
    private final byte[] secretKeyBytes;
    private final String region;
    private final String service;

    public IamV4Signer(String accessKey, String secretKey, String region, String service) {
        this.accessKey = accessKey;
        this.secretKeyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        this.region = region;
        this.service = service;
    }

    /**
     * Generate a fresh X-Date timestamp.
     */
    public static String generateXDate() {
        return TIME_FMT.format(ZonedDateTime.now(ZoneOffset.UTC));
    }

    /**
     * Generate the Authorization header value.
     * @param xDate the X-Date header value (must match what's sent in the request)
     */
    public String sign(String method, String path, String query, String payload, String host, String xDate) {
        String dateStamp = xDate.substring(0, 8); // "YYYYMMDD" from "YYYYMMDDTHHMMSSZ"

        String hashedPayload = sha256Hex(payload);

        // Canonical Request
        String canonicalHeaders = "content-type:application/json\n"
                + "host:" + host + "\n"
                + "x-date:" + xDate + "\n";
        String signedHeaders = "content-type;host;x-date";
        String canonicalRequest = method + "\n"
                + path + "\n"
                + query + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + hashedPayload;

        // Credential Scope
        String credentialScope = dateStamp + "/" + region + "/" + service + "/request";

        // String to Sign
        String stringToSign = ALGORITHM + "\n"
                + xDate + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

        // Signing key derivation — Volcengine uses raw secret key (no prefix)
        byte[] kDate = hmacSha256(secretKeyBytes, dateStamp);
        byte[] kRegion = hmacSha256(kDate, region);
        byte[] kService = hmacSha256(kRegion, service);
        byte[] kSigning = hmacSha256(kService, "request");

        // Signature
        byte[] signature = hmacSha256(kSigning, stringToSign);
        String signatureHex = bytesToHex(signature);

        return ALGORITHM + " Credential=" + accessKey + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signatureHex;
    }

    // ── Crypto helpers ──

    static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("HMAC-SHA256 failed", e);
        }
    }

    static String sha256Hex(String data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 failed", e);
        }
    }

    static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }
}
