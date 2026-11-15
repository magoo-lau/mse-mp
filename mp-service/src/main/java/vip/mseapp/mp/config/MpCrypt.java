package vip.mseapp.mp.config;

import org.springframework.stereotype.Component;
import vip.mseapp.core.exception.ServiceException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 敏感字段加密器：AppSecret / CallbackToken / EncodingAESKey / access_token 落库前加密，接口不回显。
 *
 * <p>AES-256-GCM，密钥由配置 mse.mp.crypt-key 经 SHA-256 派生；
 * 密文格式 "v1:" + Base64(nonce(12B) + ciphertext+tag)。
 */
@Component
public class MpCrypt {

    private static final String PREFIX = "v1:";
    private static final int NONCE_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public MpCrypt(MpProperties properties) {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest(properties.getCryptKey().getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM 缺少 SHA-256 算法", e);
        }
    }

    /** 加密；空值原样返回 */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        try {
            byte[] nonce = new byte[NONCE_LEN];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[NONCE_LEN + encrypted.length];
            System.arraycopy(nonce, 0, out, 0, NONCE_LEN);
            System.arraycopy(encrypted, 0, out, NONCE_LEN, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new ServiceException(500, "敏感字段加密失败：" + e.getMessage());
        }
    }

    /** 解密；空值原样返回 */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
            return ciphertext;
        }
        try {
            byte[] all = Base64.getDecoder().decode(ciphertext.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, all, 0, NONCE_LEN));
            byte[] plain = cipher.doFinal(all, NONCE_LEN, all.length - NONCE_LEN);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // 密钥更换后历史密文不可解，直接提示重新录入凭据而非静默失败
            throw new ServiceException(500, "敏感数据解密失败（加密密钥可能已变更，请重新录入凭据）");
        }
    }
}
