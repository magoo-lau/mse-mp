package vip.mseapp.mp.wx;

import vip.mseapp.core.exception.ServiceException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 微信消息体加解密（公众平台服务器配置的「安全模式/兼容模式」）。
 *
 * <p>协议要点（与微信官方实现一致）：
 * <ul>
 *   <li>EncodingAESKey 为 43 位 Base64 字符，补一个 '=' 解码后即 32 字节 AES-256 密钥；</li>
 *   <li>CBC 模式、NoPadding，IV 取密钥前 16 字节；</li>
 *   <li>明文结构：random(16B) + msg_len(4B 大端) + msg + receiveid(AppID)；</li>
 *   <li>PKCS7 填充块大小为 32（微信官方实现，非标准 16）。</li>
 * </ul>
 */
public class WxCrypto {

    private static final int PAD_BLOCK = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] aesKey;
    private final String appId;

    public WxCrypto(String encodingAesKey, String appId) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(encodingAesKey + "=");
        } catch (IllegalArgumentException e) {
            throw new ServiceException(400, "EncodingAESKey 非法（应为 43 位 Base64 字符）");
        }
        if (key.length != 32) {
            throw new ServiceException(400, "EncodingAESKey 非法（解码后须为 32 字节）");
        }
        this.aesKey = key;
        this.appId = appId;
    }

    /** 解密回调密文，返回明文 XML */
    public String decrypt(String cipherTextBase64) {
        byte[] decrypted;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16)));
            decrypted = cipher.doFinal(Base64.getDecoder().decode(cipherTextBase64));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new ServiceException(400, "消息解密失败：" + e.getMessage());
        }
        byte[] plain = unpad(decrypted);
        if (plain.length < 20) {
            throw new ServiceException(400, "消息解密失败：明文长度不足");
        }
        int msgLen = ((plain[16] & 0xFF) << 24) | ((plain[17] & 0xFF) << 16)
                | ((plain[18] & 0xFF) << 8) | (plain[19] & 0xFF);
        if (20L + msgLen > plain.length) {
            throw new ServiceException(400, "消息解密失败：长度字段越界");
        }
        String receiveId = new String(plain, 20 + msgLen, plain.length - 20 - msgLen, StandardCharsets.UTF_8);
        if (!appId.equals(receiveId)) {
            throw new ServiceException(400, "消息解密失败：receiveid 与 AppID 不一致");
        }
        return new String(plain, 20, msgLen, StandardCharsets.UTF_8);
    }

    /** 加密被动回复，返回 Base64 密文 */
    public String encrypt(String plaintextXml) {
        byte[] msg = plaintextXml.getBytes(StandardCharsets.UTF_8);
        byte[] receiveId = appId.getBytes(StandardCharsets.UTF_8);
        byte[] random = new byte[16];
        RANDOM.nextBytes(random);

        int total = 16 + 4 + msg.length + receiveId.length;
        int pad = PAD_BLOCK - total % PAD_BLOCK;
        byte[] plain = Arrays.copyOf(random, total + pad);
        plain[16] = (byte) (msg.length >>> 24);
        plain[17] = (byte) (msg.length >>> 16);
        plain[18] = (byte) (msg.length >>> 8);
        plain[19] = (byte) msg.length;
        System.arraycopy(msg, 0, plain, 20, msg.length);
        System.arraycopy(receiveId, 0, plain, 20 + msg.length, receiveId.length);
        for (int i = 0; i < pad; i++) {
            plain[total + i] = (byte) pad;
        }

        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey, "AES"),
                    new IvParameterSpec(Arrays.copyOfRange(aesKey, 0, 16)));
            return Base64.getEncoder().encodeToString(cipher.doFinal(plain));
        } catch (GeneralSecurityException e) {
            throw new ServiceException(500, "回复加密失败：" + e.getMessage());
        }
    }

    /** PKCS7（块 32）去填充：末尾字节即填充长度 */
    private static byte[] unpad(byte[] data) {
        if (data.length == 0) {
            return data;
        }
        int pad = data[data.length - 1] & 0xFF;
        if (pad < 1 || pad > PAD_BLOCK || pad > data.length) {
            return data;
        }
        return Arrays.copyOf(data, data.length - pad);
    }
}
