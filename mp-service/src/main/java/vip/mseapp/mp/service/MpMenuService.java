package vip.mseapp.mp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.mp.entity.MpAccount;
import vip.mseapp.mp.entity.MpMenu;
import vip.mseapp.mp.mapper.MpMenuMapper;
import vip.mseapp.mp.wx.WxClient;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自定义菜单管理：本地草稿（与微信 menu/create 同构 JSON）+ 同步/拉取/删除线上菜单。
 *
 * <p>本地草稿与线上菜单独立——保存草稿不等于发布。未认证公众号调用菜单 API
 * 微信会返回 48001（api unauthorized），原样透出并附提示。
 */
@Service
public class MpMenuService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** 微信菜单按钮类型（官网专有类型由前端只读展示） */
    private static final Set<String> MENU_TYPES = Set.of(
            "click", "view", "scancode_push", "scancode_waitmsg", "pic_sysphoto",
            "pic_photo_or_album", "pic_weixin", "location_select",
            "media_id", "view_limited", "article_id", "article_view_limited", "miniprogram");

    private final MpMenuMapper menuMapper;
    private final MpTokenManager tokenManager;
    private final WxClient wxClient;

    public MpMenuService(MpMenuMapper menuMapper, MpTokenManager tokenManager, WxClient wxClient) {
        this.menuMapper = menuMapper;
        this.tokenManager = tokenManager;
        this.wxClient = wxClient;
    }

    /** 草稿视图（无草稿时返回空菜单） */
    public DraftView draft(MpAccount account) {
        MpMenu menu = menuMapper.selectOne(new LambdaQueryWrapper<MpMenu>()
                .eq(MpMenu::getAccountId, account.getId()));
        if (menu == null) {
            return new DraftView("{\"button\":[]}", MpMenu.STATUS_DRAFT, null);
        }
        return new DraftView(menu.getMenuJson(), menu.getStatus(), menu.getSyncedTime());
    }

    /** 保存草稿（结构校验，不调微信） */
    public void saveDraft(MpAccount account, String menuJson) {
        validate(menuJson);
        MpMenu menu = menuMapper.selectOne(new LambdaQueryWrapper<MpMenu>()
                .eq(MpMenu::getAccountId, account.getId()));
        if (menu == null) {
            menu = new MpMenu();
            menu.setAccountId(account.getId());
            menu.setMenuJson(menuJson);
            menu.setStatus(MpMenu.STATUS_DRAFT);
            menuMapper.insert(menu);
        } else {
            menu.setMenuJson(menuJson);
            menu.setStatus(MpMenu.STATUS_DRAFT);
            menuMapper.updateById(menu);
        }
    }

    /** 同步到微信（覆盖线上菜单） */
    public void sync(MpAccount account) {
        MpMenu menu = menuMapper.selectOne(new LambdaQueryWrapper<MpMenu>()
                .eq(MpMenu::getAccountId, account.getId()));
        if (menu == null || menu.getMenuJson() == null || menu.getMenuJson().isBlank()) {
            throw new ServiceException(400, "请先保存菜单草稿");
        }
        validate(menu.getMenuJson());
        String token = tokenManager.getToken(account, false);
        JsonNode resp = wxClient.createMenu(token, menu.getMenuJson());
        int errCode = resp.path("errcode").asInt(0);
        if (errCode != 0) {
            throw new ServiceException(500, "菜单同步失败[" + errCode + "]：" + resp.path("errmsg").asText("")
                    + (errCode == 48001 ? "（公众号未认证，无自定义菜单权限）" : ""));
        }
        menu.setStatus(MpMenu.STATUS_SYNCED);
        menu.setSyncedTime(LocalDateTime.now());
        menuMapper.updateById(menu);
    }

    /** 拉取线上菜单，归一化为 {"button":[{name,type,key,url,sub_button:[...]}]} */
    public Map<String, Object> remote(MpAccount account) {
        String token = tokenManager.getToken(account, false);
        String raw = wxClient.currentSelfMenuRaw(token);
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.startsWith("<")) {
            return normalizeXml(trimmed);
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(trimmed);
        } catch (Exception e) {
            throw new ServiceException(500, "线上菜单返回无法解析：" + abbreviate(trimmed));
        }
        int errCode = root.path("errcode").asInt(0);
        if (errCode != 0) {
            throw new ServiceException(500, "拉取线上菜单失败[" + errCode + "]：" + root.path("errmsg").asText(""));
        }
        return normalizeJsonNode(root.path("selfmenu_info").path("button"));
    }

    /** 删除线上菜单（草稿保留） */
    public void deleteRemote(MpAccount account) {
        String token = tokenManager.getToken(account, false);
        JsonNode resp = wxClient.deleteMenu(token);
        int errCode = resp.path("errcode").asInt(0);
        if (errCode != 0) {
            throw new ServiceException(500, "删除线上菜单失败[" + errCode + "]：" + resp.path("errmsg").asText("")
                    + (errCode == 48001 ? "（公众号未认证，无自定义菜单权限）" : ""));
        }
        MpMenu menu = menuMapper.selectOne(new LambdaQueryWrapper<MpMenu>()
                .eq(MpMenu::getAccountId, account.getId()));
        if (menu != null) {
            menu.setStatus(MpMenu.STATUS_DRAFT);
            menu.setSyncedTime(null);
            menuMapper.updateById(menu);
        }
    }

    // ==================== 草稿结构校验 ====================

    private void validate(String menuJson) {
        JsonNode root;
        try {
            root = MAPPER.readTree(menuJson == null ? "" : menuJson);
        } catch (Exception e) {
            throw new ServiceException(400, "菜单 JSON 非法：" + e.getMessage());
        }
        JsonNode buttons = root.path("button");
        if (!buttons.isArray() || buttons.isEmpty()) {
            throw new ServiceException(400, "button 须为非空数组（1-3 个一级菜单）");
        }
        if (buttons.size() > 3) {
            throw new ServiceException(400, "一级菜单最多 3 个");
        }
        for (JsonNode button : buttons) {
            validateButton(button, true);
            JsonNode subs = button.path("sub_button");
            if (subs.isArray() && subs.size() > 5) {
                throw new ServiceException(400, "二级菜单最多 5 个");
            }
            if (subs.isArray()) {
                for (JsonNode sub : subs) {
                    validateButton(sub, false);
                }
            }
        }
    }

    private void validateButton(JsonNode button, boolean top) {
        String name = button.path("name").asText("").trim();
        if (name.isEmpty() || name.length() > 30) {
            throw new ServiceException(400, (top ? "一级" : "二级") + "菜单名称须为 1-30 字");
        }
        JsonNode subs = button.path("sub_button");
        if (subs.isArray() && !subs.isEmpty()) {
            return; // 有子菜单时本按钮仅为父级
        }
        String type = button.path("type").asText("").trim();
        if (!MENU_TYPES.contains(type)) {
            throw new ServiceException(400, "菜单类型非法或缺失：" + type);
        }
        switch (type) {
            case "click" -> requireText(button, "key", "click 类型须填写 Key");
            case "view" -> requireText(button, "url", "view 类型须填写网页链接");
            case "miniprogram" -> {
                requireText(button, "url", "miniprogram 类型须填写网页链接（备用）");
                requireText(button, "pagepath", "miniprogram 类型须填写小程序页面路径");
                requireText(button, "appid", "miniprogram 类型须填写小程序 AppID");
            }
            case "media_id", "view_limited", "article_id", "article_view_limited" ->
                    requireText(button, "media_id", type + " 类型须填写素材 media_id");
            default -> requireText(button, "key", type + " 类型须填写 Key");
        }
    }

    private void requireText(JsonNode node, String field, String message) {
        if (node.path(field).asText("").isBlank()) {
            throw new ServiceException(400, message);
        }
    }

    // ==================== 线上菜单归一化 ====================

    /** JSON 结构：selfmenu_info.button[]，子按钮可能是 {list:[...]} 或数组 */
    private Map<String, Object> normalizeJsonNode(JsonNode buttons) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("button", normalizeJsonButtons(buttons));
        return root;
    }

    private List<Map<String, Object>> normalizeJsonButtons(JsonNode buttons) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode button : buttons) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", button.path("name").asText(""));
            putIfPresent(item, "type", button, "type");
            putIfPresent(item, "key", button, "key");
            putIfPresent(item, "url", button, "url");
            putIfPresent(item, "media_id", button, "media_id");
            JsonNode subs = button.path("sub_button");
            if (subs.isArray()) {
                if (!subs.isEmpty()) {
                    item.put("sub_button", normalizeJsonButtons(subs));
                }
            } else if (subs.isObject()) {
                List<Map<String, Object>> subList = normalizeJsonButtons(subs.path("list"));
                if (!subList.isEmpty()) {
                    item.put("sub_button", subList);
                }
            }
            result.add(item);
        }
        return result;
    }

    /** XML 结构（get_current_selfmenu_info 的兼容返回）：<selfmenu_info><button>…<sub_button><item>… */
    private Map<String, Object> normalizeXml(String xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            var root = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)))
                    .getDocumentElement();
            Element selfmenu = firstChild(root, "selfmenu_info");
            if (selfmenu == null) {
                throw new ServiceException(500, "线上菜单返回缺少 selfmenu_info");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("button", normalizeXmlButtons(selfmenu, "button"));
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw new ServiceException(500, "线上菜单 XML 解析失败：" + e.getMessage());
        }
    }

    private List<Map<String, Object>> normalizeXmlButtons(Element parent, String childTag) {
        List<Map<String, Object>> result = new ArrayList<>();
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (!(children.item(i) instanceof Element element) || !childTag.equals(element.getTagName())) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            Element name = firstChild(element, "name");
            item.put("name", name != null ? name.getTextContent() : "");
            copyXmlField(element, item, "type", "type");
            copyXmlField(element, item, "key", "key");
            copyXmlField(element, item, "url", "url");
            copyXmlField(element, item, "media_id", "media_id");
            Element subButton = firstChild(element, "sub_button");
            if (subButton != null) {
                List<Map<String, Object>> subs = normalizeXmlButtons(subButton, "item");
                if (!subs.isEmpty()) {
                    item.put("sub_button", subs);
                }
            }
            result.add(item);
        }
        return result;
    }

    private void copyXmlField(Element parent, Map<String, Object> target, String tag, String key) {
        Element child = firstChild(parent, tag);
        if (child != null && !child.getTextContent().isBlank()) {
            target.put(key, child.getTextContent());
        }
    }

    private static Element firstChild(Element parent, String tag) {
        var children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element && tag.equals(element.getTagName())) {
                return element;
            }
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> target, String key, JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (!value.isBlank()) {
            target.put(key, value);
        }
    }

    private static String abbreviate(String text) {
        return text.length() > 200 ? text.substring(0, 200) + "..." : text;
    }

    /** 草稿视图 */
    public record DraftView(String menuJson, Integer status, LocalDateTime syncedTime) {
    }
}
