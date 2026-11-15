package vip.mseapp.mp.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.mp.entity.MpReplyRule;
import vip.mseapp.mp.mapper.MpReplyRuleMapper;

import java.util.Set;

/**
 * 自动回复规则管理。
 *
 * <p>匹配语义见 {@link MpReplyRule}；此处负责 CRUD 与 replyContent JSON 校验：
 * text → {"content":"..."}；news → {"items":[{title,description,picUrl,url},...]}（≤8 条）。
 */
@Service
public class MpRuleService {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    private static final Set<String> MATCH_TYPES = Set.of(
            MpReplyRule.MATCH_SUBSCRIBE, MpReplyRule.MATCH_UNSUBSCRIBE, MpReplyRule.MATCH_CLICK,
            MpReplyRule.MATCH_MSG_ANY, MpReplyRule.MATCH_KEYWORD_EXACT,
            MpReplyRule.MATCH_KEYWORD_CONTAINS, MpReplyRule.MATCH_DEFAULT);
    private static final Set<String> REPLY_TYPES = Set.of(MpReplyRule.REPLY_TEXT, MpReplyRule.REPLY_NEWS);
    private static final Set<String> MATCH_VALUE_REQUIRED = Set.of(
            MpReplyRule.MATCH_CLICK, MpReplyRule.MATCH_KEYWORD_EXACT, MpReplyRule.MATCH_KEYWORD_CONTAINS);

    private final MpReplyRuleMapper ruleMapper;

    public MpRuleService(MpReplyRuleMapper ruleMapper) {
        this.ruleMapper = ruleMapper;
    }

    public PageResult<MpReplyRule> page(long pageNum, long pageSize, Long accountId, String keyword) {
        LambdaQueryWrapper<MpReplyRule> wrapper = new LambdaQueryWrapper<MpReplyRule>()
                .eq(accountId != null, MpReplyRule::getAccountId, accountId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(MpReplyRule::getName, keyword)
                        .or().like(MpReplyRule::getMatchValue, keyword))
                .orderByDesc(MpReplyRule::getPriority)
                .orderByAsc(MpReplyRule::getId);
        Page<MpReplyRule> page = ruleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(Long accountId, String name, String matchType, String matchValue,
                       String replyType, String replyContent, Integer priority, Integer enabled) {
        validate(matchType, matchValue, replyType, replyContent);
        MpReplyRule rule = new MpReplyRule();
        rule.setAccountId(accountId);
        rule.setName(name);
        rule.setMatchType(matchType);
        rule.setMatchValue(matchValue);
        rule.setReplyType(replyType);
        rule.setReplyContent(replyContent);
        rule.setPriority(priority == null ? 0 : priority);
        rule.setEnabled(enabled == null ? 1 : enabled);
        ruleMapper.insert(rule);
        return rule.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, String name, String matchType, String matchValue,
                       String replyType, String replyContent, Integer priority, Integer enabled) {
        validate(matchType, matchValue, replyType, replyContent);
        MpReplyRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new ServiceException(404, "规则不存在");
        }
        rule.setName(name);
        rule.setMatchType(matchType);
        rule.setMatchValue(matchValue);
        rule.setReplyType(replyType);
        rule.setReplyContent(replyContent);
        rule.setPriority(priority == null ? 0 : priority);
        rule.setEnabled(enabled == null ? 1 : enabled);
        ruleMapper.updateById(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void changeEnabled(Long id, boolean enable) {
        MpReplyRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new ServiceException(404, "规则不存在");
        }
        rule.setEnabled(enable ? 1 : 0);
        ruleMapper.updateById(rule);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (ruleMapper.selectById(id) == null) {
            throw new ServiceException(404, "规则不存在");
        }
        ruleMapper.deleteById(id);
    }

    private void validate(String matchType, String matchValue, String replyType, String replyContent) {
        if (matchType == null || !MATCH_TYPES.contains(matchType)) {
            throw new ServiceException(400, "匹配类型非法");
        }
        if (MATCH_VALUE_REQUIRED.contains(matchType)
                && (matchValue == null || matchValue.isBlank())) {
            throw new ServiceException(400, "该匹配类型须填写匹配值（菜单 Key / 关键词）");
        }
        if (replyType == null || !REPLY_TYPES.contains(replyType)) {
            throw new ServiceException(400, "回复类型仅支持 text/news");
        }
        JsonNode json;
        try {
            json = MAPPER.readTree(replyContent == null ? "" : replyContent);
        } catch (Exception e) {
            throw new ServiceException(400, "回复内容须为合法 JSON");
        }
        if (MpReplyRule.REPLY_NEWS.equals(replyType)) {
            JsonNode items = json.path("items");
            if (!items.isArray() || items.isEmpty()) {
                throw new ServiceException(400, "图文回复须至少 1 条图文");
            }
            if (items.size() > 8) {
                throw new ServiceException(400, "图文回复最多 8 条");
            }
            for (JsonNode item : items) {
                if (item.path("title").asText("").isBlank()) {
                    throw new ServiceException(400, "图文标题不能为空");
                }
            }
        } else if (json.path("content").asText("").isBlank()) {
            throw new ServiceException(400, "文本回复内容不能为空");
        }
    }
}
