package vip.mseapp.mp.ai;

import org.springframework.stereotype.Component;
import vip.mseapp.ai.api.AiBizModuleProvider;
import vip.mseapp.ai.api.dto.AiBizModuleDescriptor;

import java.util.List;

/**
 * MP 业务模块声明：把「微信公众号」模块自声明给 mse-ai 模块注册中心——通用业务分析入口
 * （biz_query 路由门面）据此把粉丝/消息/菜单等问题路由到本模块查询工具 mp_biz_query
 * （{@code MpBizQueryTool}，在 mse-mp 进程内执行）。单体本地扫描 / 微服务推送自动登记。
 */
@Component
public class MpBizModuleProvider implements AiBizModuleProvider {

    @Override
    public List<AiBizModuleDescriptor> modules() {
        AiBizModuleDescriptor descriptor = new AiBizModuleDescriptor();
        descriptor.setModuleCode("mp");
        descriptor.setName("微信公众号");
        descriptor.setDescription("公众号账号、粉丝关注、消息交互、菜单、素材、模板消息");
        descriptor.setServiceName("mse-mp");
        descriptor.setKeywords("公众号,粉丝,消息,菜单,素材,模板消息,关注,互动");
        descriptor.setTablePrefixes("mp_");
        descriptor.setToolCode("mp_biz_query");
        return List.of(descriptor);
    }
}
