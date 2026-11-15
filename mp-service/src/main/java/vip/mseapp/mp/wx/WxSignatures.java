package vip.mseapp.mp.wx;

import vip.mseapp.core.exception.ServiceException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 微信回调验签：signature = sha1(字典序排序(token, timestamp, nonce[, encrypt]))。
 */
public final class WxSignatures {

    private WxSignatures() {
    }

    /** 明文模式验签（接入验证与消息推送均为此签名） */
    public static String signature(String token, String timestamp, String nonce) {
        return sha1(sortedJoin(List.of(token, timestamp, nonce)));
    }

    /** 密文模式验签（compatible/safe 模式消息推送：多一个 encrypt 密文参与排序） */
    public static String signature(String token, String timestamp, String nonce, String encrypt) {
        return sha1(sortedJoin(List.of(token, timestamp, nonce, encrypt)));
    }

    private static String sortedJoin(List<String> items) {
        List<String> sorted = new ArrayList<>(items);
        Collections.sort(sorted);
        return String.join("", sorted);
    }

    private static String sha1(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new ServiceException(500, "JVM 缺少 SHA-1 算法");
        }
    }
}
