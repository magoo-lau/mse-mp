package vip.mseapp.mp.autoconfigure;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import vip.mseapp.mybatis.config.MseMybatisAutoConfiguration;
import vip.mseapp.mp.api.RemoteMpFanService;
import vip.mseapp.mp.api.RemoteMpMessageService;
import vip.mseapp.mp.openapi.MpOpenApiContributor;
import vip.mseapp.mp.rpc.RemoteMpFanServiceImpl;
import vip.mseapp.mp.rpc.RemoteMpMessageServiceImpl;
import vip.mseapp.mp.service.MpFanService;
import vip.mseapp.mp.service.MpMessageService;
import vip.mseapp.open.api.OpenApiContributor;
import vip.mseapp.core.annotation.TenantServiceModule;

/**
 * 公众号管理业务服务自动装配（Spring Boot SPI）。
 *
 * <p>接入方仅需引入 mp-service 依赖：Service / 支撑组件（加解密、配置、token、微信客户端）
 * / Mapper 均由此注册，无需 component-scan 业务包名，也无需 @MapperScan。
 * 控制器由 {@link MpControllerAutoConfiguration} 装配，两者均经
 * {@code META-INF/spring/...AutoConfiguration.imports} 登记。
 */
@AutoConfiguration(before = MseMybatisAutoConfiguration.class)
@MapperScan(basePackages = "vip.mseapp.mp.mapper", annotationClass = Mapper.class)
@ComponentScan(basePackages = {"vip.mseapp.mp.config", "vip.mseapp.mp.service", "vip.mseapp.mp.wx"})
@TenantServiceModule("mse-mp")
public class MpServiceAutoConfiguration {

    /** 开放平台粉丝契约实现（只读）：可声明同契约 Bean 整体替换 */
    @Bean
    @ConditionalOnMissingBean(RemoteMpFanService.class)
    public RemoteMpFanService remoteMpFanService(MpFanService fanService,
                                                 tools.jackson.databind.json.JsonMapper objectMapper) {
        return new RemoteMpFanServiceImpl(fanService, objectMapper);
    }

    /** 开放平台消息契约实现（只读）：可声明同契约 Bean 整体替换 */
    @Bean
    @ConditionalOnMissingBean(RemoteMpMessageService.class)
    public RemoteMpMessageService remoteMpMessageService(MpMessageService messageService,
                                                         tools.jackson.databind.json.JsonMapper objectMapper) {
        return new RemoteMpMessageServiceImpl(messageService, objectMapper);
    }

    /** 开放能力声明（开放平台自注册；按 Bean 名判重，避免多积木同类型贡献者互相让位） */
    @Bean(name = "mpOpenApiContributor")
    @ConditionalOnMissingBean(name = "mpOpenApiContributor")
    public OpenApiContributor mpOpenApiContributor() {
        return new MpOpenApiContributor();
    }
}
