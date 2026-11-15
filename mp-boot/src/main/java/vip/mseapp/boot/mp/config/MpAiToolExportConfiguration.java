package vip.mseapp.boot.mp.config;

import java.util.List;

import org.apache.dubbo.config.ServiceConfig;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import vip.mseapp.ai.api.RemoteAiTool;
import vip.mseapp.ai.api.support.DispatchingAiTool;
import vip.mseapp.mp.aitool.MpBizQueryTool;
import vip.mseapp.rpc.dubbo.MseDubboSupport;

/**
 * MP 工具 Dubbo 导出（仅微服务 dubbo 模式）：group=spring.application.name（mse-mp），
 * mse-ai 平台按 group 泛化调用。当前承载 mp_biz_query（业务库受控查询，biz_query 模块化
 * 路由的 mp 分支）；后续新增工具时收敛进 {@link DispatchingAiTool}（同 group 只能导出
 * 一个 RemoteAiTool 实现）。
 *
 * <p>放在 mp-boot 而非 mp-service：Dubbo 依赖属启动器形态（mp-service 纯 jar 无 Dubbo classpath）；
 * 单体聚合形态无需导出——mse-ai 直接收集容器内 RemoteAiTool Bean（local 分发按 toolCode）。
 */
@Configuration
@ConditionalOnProperty(name = "mse.rpc.mode", havingValue = "dubbo")
public class MpAiToolExportConfiguration {

    @Bean(initMethod = "export", destroyMethod = "unexport")
    public ServiceConfig<RemoteAiTool> mpAiToolConfig(MpBizQueryTool mpBizQueryTool,
                                                      ConfigurableListableBeanFactory beanFactory,
                                                      Environment environment) {
        ServiceConfig<RemoteAiTool> config = MseDubboSupport.serviceConfig(RemoteAiTool.class,
                new DispatchingAiTool(List.of(mpBizQueryTool)), beanFactory);
        config.setGroup(environment.getProperty("spring.application.name"));
        return config;
    }
}
