package com.example.mystore.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

/**
 * 微信小程序消息推送加解密工具(安全模式)
 * 算法参考微信官方 Java 示例 WXBizMsgCrypt
 */
public class WxMsgCrypt {

    private WxMsgCrypt() {
    }

    /** GET 服务器验证签名: sha1(sort(token, timestamp, nonce)) */
    public static boolean checkSignature(String token, String timestamp, String nonce, String signature) {
        if (token == null || signature == null) {
            return false;
        }
        String[] arr = {token, timestamp, nonce};
        Arrays.sort(arr);
        return sha1(String.join("", arr)).equals(signature);
    }

    /** POST 安全模式签名: sha1(sort(token, timestamp, nonce, encrypt)) —— 注意不是 signature */
    public static boolean checkMsgSignature(String token, String timestamp, String nonce,
                                            String encrypt, String msgSignature) {
        if (token == null || msgSignature == null) {
            return false;
        }
        String[] arr = {token, timestamp, nonce, encrypt};
        Arrays.sort(arr);
        return sha1(String.join("", arr)).equals(msgSignature);
    }

    /**
     * 解密 Encrypt: AES-256-CBC/PKCS#7; key = Base64Decode(EncodingAESKey + "="), IV = key[0..16]
     * 明文结构: random(16B) + msgLen(4B, 网络字节序) + msg + appid
     *
     * @return 解密后的 msg(JSON 字符串)
     */
    public static String decrypt(String encodingAesKey, String encrypt, String appid) {
        try {
            byte[] aesKey = Base64.getDecoder().decode(encodingAesKey + "=");
            byte[] cipherData = Base64.getDecoder().decode(encrypt);

            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec keySpec = new SecretKeySpec(aesKey, "AES");
            IvParameterSpec iv = new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16));
            cipher.init(Cipher.DECRYPT_MODE, keySpec, iv);
            byte[] original = cipher.doFinal(cipherData);

            // 去除 PKCS#7 填充
            int pad = original[original.length - 1];
            if (pad < 1 || pad > 32) {
                pad = 0;
            }
            byte[] bytes = Arrays.copyOfRange(original, 0, original.length - pad);

            int msgLen = ByteBuffer.wrap(bytes, 16, 4).getInt();
            String msg = new String(bytes, 20, msgLen, StandardCharsets.UTF_8);
            String fromAppid = new String(bytes, 20 + msgLen, bytes.length - 20 - msgLen, StandardCharsets.UTF_8);
            if (appid != null && !appid.isEmpty() && !appid.equals(fromAppid)) {
                throw new IllegalStateException("appid 不匹配: " + fromAppid);
            }
            return msg;
        } catch (Exception e) {
            throw new IllegalStateException("微信消息解密失败: " + e.getMessage(), e);
        }
    }

    private static String sha1(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA1 计算失败", e);
        }
    }
}
