package vip.mseapp.mp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 微信公众号平台配置（mse.mp.*）。
 */
@Component
@ConfigurationProperties(prefix = "mse.mp")
public class MpProperties {

    /** 敏感字段 AES-GCM 落库加密密钥（生产必须修改） */
    private String cryptKey = "mse-mp-default-crypt-key-please-change";

    /** 微信 API 域名（内网联调可指向模拟器） */
    private String apiBase = "https://api.weixin.qq.com";

    /** 调用微信 API 的 HTTP 超时（毫秒） */
    private long httpTimeoutMs = 3000;

    public String getCryptKey() {
        return cryptKey;
    }

    public void setCryptKey(String cryptKey) {
        this.cryptKey = cryptKey;
    }

    public String getApiBase() {
        return apiBase;
    }

    public void setApiBase(String apiBase) {
        this.apiBase = apiBase;
    }

    public long getHttpTimeoutMs() {
        return httpTimeoutMs;
    }

    public void setHttpTimeoutMs(long httpTimeoutMs) {
        this.httpTimeoutMs = httpTimeoutMs;
    }
}
