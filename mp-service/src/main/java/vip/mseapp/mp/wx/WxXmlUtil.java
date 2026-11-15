package vip.mseapp.mp.wx;

import org.w3c.dom.Element;
import vip.mseapp.core.exception.ServiceException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 微信回调 XML 解析与被动回复 XML 构造。
 *
 * <p>回复 XML 用模板手拼（CDATA 组合标签无法用标准序列化器表达）；
 * CDATA 内容做 ]]&gt; 转义。回复的 FromUserName 必须填入站报文的 ToUserName
 * （gh_ 开头的原始 ID），填 AppID 会被微信丢弃。
 */
public final class WxXmlUtil {

    private WxXmlUtil() {
    }

    /**
     * 解析回调 XML 为扁平 Map（微信消息报文只有一层元素）。
     * 注意：不强制要求 MsgType——加密信封（compatible/safe 模式）只有 Encrypt/MsgSignature 等字段，
     * 信封解析与消息报文解析共用本方法，强制 MsgType 曾致密文推送整体失效（解密前即抛异常兜底 success）。
     */
    public static Map<String, String> parse(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            // 关闭外部实体，防 XXE（报文来自微信，仍按不信任输入处理）
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var root = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .getDocumentElement();
            Map<String, String> result = new LinkedHashMap<>();
            var children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element child) {
                    result.put(child.getTagName(), child.getTextContent());
                }
            }
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(400, "XML 解析失败：" + e.getMessage());
        }
    }

    /** 从密文报文中提取 &lt;Encrypt&gt;（compatible/safe 模式） */
    public static String extractEncrypt(Map<String, String> msg) {
        String encrypt = msg.get("Encrypt");
        if (encrypt == null || encrypt.isBlank()) {
            throw new ServiceException(400, "加密模式报文缺少 Encrypt 字段");
        }
        return encrypt;
    }

    /** 文本回复 */
    public static String textReply(String toUser, String fromUser, String content) {
        return "<xml><ToUserName>" + cdata(toUser) + "</ToUserName>"
                + "<FromUserName>" + cdata(fromUser) + "</FromUserName>"
                + "<CreateTime>" + epoch() + "</CreateTime>"
                + "<MsgType>" + cdata("text") + "</MsgType>"
                + "<Content>" + cdata(content) + "</Content></xml>";
    }

    /** 图文回复（微信单次最多 8 条，超出截断） */
    public static String newsReply(String toUser, String fromUser, List<NewsItem> items) {
        List<NewsItem> limited = items.size() > 8 ? items.subList(0, 8) : items;
        StringBuilder sb = new StringBuilder();
        sb.append("<xml><ToUserName>").append(cdata(toUser))
                .append("</ToUserName><FromUserName>").append(cdata(fromUser))
                .append("</FromUserName><CreateTime>").append(epoch())
                .append("</CreateTime><MsgType>").append(cdata("news"))
                .append("</MsgType><ArticleCount>").append(limited.size()).append("</ArticleCount><Articles>");
        for (NewsItem item : limited) {
            sb.append("<item><Title>").append(cdata(item.title()))
                    .append("</Title><Description>").append(cdata(item.description()))
                    .append("</Description><PicUrl>").append(cdata(item.picUrl()))
                    .append("</PicUrl><Url>").append(cdata(item.url()))
                    .append("</Url></item>");
        }
        sb.append("</Articles></xml>");
        return sb.toString();
    }

    /** 加密回复信封（compatible/safe 模式） */
    public static String encryptedReply(String encrypt, String signature, String timestamp, String nonce) {
        return "<xml><Encrypt>" + cdata(encrypt) + "</Encrypt>"
                + "<MsgSignature>" + cdata(signature) + "</MsgSignature>"
                + "<TimeStamp>" + timestamp + "</TimeStamp>"
                + "<Nonce>" + cdata(nonce) + "</Nonce></xml>";
    }

    /** CDATA 转义：序列中出现 ]]&gt; 时拆分拼接 */
    private static String cdata(String text) {
        String safe = text == null ? "" : text.replace("]]>", "]]]]><![CDATA[>");
        return "<![CDATA[" + safe + "]]>";
    }

    private static long epoch() {
        return System.currentTimeMillis() / 1000;
    }

    /** 图文条目 */
    public record NewsItem(String title, String description, String picUrl, String url) {
    }
}
