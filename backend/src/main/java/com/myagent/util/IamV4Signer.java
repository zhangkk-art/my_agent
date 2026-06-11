package com.myagent.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * IAM v4 HMAC-SHA256 signer for Volcengine visual API (cv service).
 * Generates Authorization header for requests to visual.volcengineapi.com.
 */
public class IamV4Signer {

    private static final String ALGORITHM = "HMAC-SHA256";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");

    private final String accessKey;
    private final String secretKey;
    private final String region;
    private final String service;

    public IamV4Signer(String accessKey, String secretKey, String region, String service) {
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.region = region;
        this.service = service;
    }

    /**
     * Generate the Authorization header value for the given request details.
     */
    public String sign(String method, String path, String query, String payload, String host) {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String xDate = TIME_FMT.format(now);
        String dateStamp = DATE_FMT.format(now);

        String hashedPayload = sha256Hex(payload);

        // Canonical Request
        String canonicalHeaders = "content-type:application/json\nhost:" + host + "\nx-date:" + xDate + "\n";
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

        // Signing key derivation
        byte[] kDate = hmacSha256(("HMAC" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
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

    /**
     * Returns the X-Date header value for this signing request.
     */
    public String getXDate() {
        return TIME_FMT.format(ZonedDateTime.now(ZoneOffset.UTC));
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
