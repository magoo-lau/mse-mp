package vip.mseapp.mp.aitool;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import vip.mseapp.ai.api.support.AbstractBizQueryTool;
import vip.mseapp.ai.api.support.AiBizQueryProperties;
import vip.mseapp.mybatis.datasource.TenantRawConnectionRouter;

import java.util.List;

/**
 * 微信公众号业务查询工具（mp_biz_query）：本模块业务表（mp_ 前缀）的受控 text2sql 直查通道，
 * 继承 ai-api 基类在 mse-mp 进程内执行——连接按本模块 @TenantServiceModule("mse-mp") 路由
 * 本模块世界，数据不出归属服务。mse-ai 的 biz_query 路由门面按模块注册表转发到本工具。
 */
@Component
public class MpBizQueryTool extends AbstractBizQueryTool {

    public MpBizQueryTool(JsonMapper objectMapper,
                          ObjectProvider<TenantRawConnectionRouter> masterConnections,
                          AiBizQueryProperties properties) {
        super(objectMapper, masterConnections, properties);
    }

    @Override
    protected String toolCode() {
        return "mp_biz_query";
    }

    @Override
    protected String toolName() {
        return "微信公众号业务查询";
    }

    @Override
    protected String toolDescription() {
        return "查询微信公众号模块的生产业务表：公众号账号、粉丝、消息交互、菜单、素材、模板消息等";
    }

    @Override
    public String moduleCode() {
        return "mp";
    }

    @Override
    public String moduleName() {
        return "微信公众号";
    }

    @Override
    protected List<String> tablePrefixes() {
        return List.of("mp_");
    }
}
