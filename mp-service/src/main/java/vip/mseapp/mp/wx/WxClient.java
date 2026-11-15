package vip.mseapp.mp.wx;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.mp.config.MpProperties;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 微信 API 最小客户端（Java 21 HttpClient，无第三方 SDK）。
 *
 * <p>仅封装本平台用到的接口：access_token / 用户基本信息 / 菜单增删查。
 * 微信约定 HTTP 200 时也可能返回 errcode != 0（业务错误），调用方须检查返回 JSON 的 errcode。
 */
@Component
public class WxClient {

    private static final Logger log = LoggerFactory.getLogger(WxClient.class);
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient http;
    private final MpProperties properties;

    public WxClient(MpProperties properties) {
        this.properties = properties;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getHttpTimeoutMs()))
                .build();
    }

    /** 获取 access_token：GET /cgi-bin/token */
    public JsonNode accessToken(String appId, String appSecret) {
        String query = "grant_type=client_credential&appid=" + urlEncode(appId)
                + "&secret=" + urlEncode(appSecret);
        return getJson("/cgi-bin/token", query);
    }

    /** 用户基本信息（粉丝资料补全）：GET /cgi-bin/user/info */
    public JsonNode userInfo(String accessToken, String openId) {
        String query = "access_token=" + urlEncode(accessToken)
                + "&openid=" + urlEncode(openId) + "&lang=zh_CN";
        return getJson("/cgi-bin/user/info", query);
    }

    /** 创建/覆盖自定义菜单：POST /cgi-bin/menu/create */
    public JsonNode createMenu(String accessToken, String menuJson) {
        return postJson("/cgi-bin/menu/create", "access_token=" + urlEncode(accessToken), menuJson);
    }

    /** 删除自定义菜单：GET /cgi-bin/menu/delete */
    public JsonNode deleteMenu(String accessToken) {
        return getJson("/cgi-bin/menu/delete", "access_token=" + urlEncode(accessToken));
    }

    /** 拉取线上菜单（返回体可能是 JSON 也可能是 XML，交由调用方归一化）：GET /cgi-bin/get_current_selfmenu_info */
    public String currentSelfMenuRaw(String accessToken) {
        return getRaw("/cgi-bin/get_current_selfmenu_info", "access_token=" + urlEncode(accessToken));
    }

    private JsonNode getJson(String path, String query) {
        return parseJson(getRaw(path, query));
    }

    private JsonNode postJson(String path, String query, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getApiBase() + path + "?" + query))
                    .timeout(Duration.ofMillis(properties.getHttpTimeoutMs()))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return parseJson(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(500, "微信 API 调用被中断");
        } catch (Exception e) {
            log.warn("[mse-mp] 微信 API 调用失败: path={}", path, e);
            throw new ServiceException(500, "微信 API 网络调用失败：" + e.getMessage());
        }
    }

    private String getRaw(String path, String query) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getApiBase() + path + "?" + query))
                    .timeout(Duration.ofMillis(properties.getHttpTimeoutMs()))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceException(500, "微信 API 调用被中断");
        } catch (Exception e) {
            log.warn("[mse-mp] 微信 API 调用失败: path={}", path, e);
            throw new ServiceException(500, "微信 API 网络调用失败：" + e.getMessage());
        }
    }

    private static JsonNode parseJson(String body) {
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new ServiceException(500, "微信 API 返回非 JSON：" + abbreviate(body));
        }
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
