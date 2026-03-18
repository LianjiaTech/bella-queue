package com.ke.bella.batch.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Hex;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * ASR签名工具类
 * 用于回调签名验证和用量上报签名计算
 */
@Slf4j
public class AsrSignatureUtils {

    private static final String HMAC_SHA256_ALGORITHM = "HmacSHA256";

    /**
     * 验证回调签名
     *
     * @param payload 回调数据的JSON字符串
     * @param signature 客户端提供的签名
     * @param secret 密钥
     * @return 签名是否有效
     */
    public static boolean verifyCallbackSignature(String payload, String signature, String secret) {
        if (payload == null || signature == null || secret == null) {
            log.warn("验证签名参数不能为空 - payload={}, signature={}, secret={}",
                    payload != null, signature != null, secret != null);
            return false;
        }

        try {
            String calculatedSignature = calculateHmacSha256(payload, secret);
            boolean isValid = calculatedSignature.equalsIgnoreCase(signature);

            if (!isValid) {
                log.warn("签名验证失败 - expected={}, actual={}", calculatedSignature, signature);
            }

            return isValid;
        } catch (Exception e) {
            log.error("签名验证异常", e);
            return false;
        }
    }

    /**
     * 计算HMAC-SHA256签名
     *
     * @param data 待签名数据
     * @param secret 密钥
     * @return 十六进制签名字符串
     * @throws NoSuchAlgorithmException 算法不存在
     * @throws InvalidKeyException 密钥无效
     */
    public static String calculateHmacSha256(String data, String secret)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_SHA256_ALGORITHM);
        SecretKeySpec secretKeySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                HMAC_SHA256_ALGORITHM
        );
        mac.init(secretKeySpec);

        byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Hex.encodeHexString(hmacBytes);
    }

    /**
     * 计算HMAC-SHA256签名（用于用量上报）
     *
     * @param data 待签名数据
     * @param secret 密钥
     * @return 十六进制签名字符串，如果出现异常返回null
     */
    public static String calculateSignatureForUsageReport(String data, String secret) {
        try {
            return calculateHmacSha256(data, secret);
        } catch (Exception e) {
            log.error("计算用量上报签名失败", e);
            return null;
        }
    }
}
