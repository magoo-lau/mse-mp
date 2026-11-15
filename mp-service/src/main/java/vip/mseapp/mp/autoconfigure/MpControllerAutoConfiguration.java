package vip.mseapp.mp.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 公众号管理控制器自动装配（Spring Boot SPI）。
 *
 * <p>仅在 Servlet Web 环境注册；可用 {@code mse.service.mp.controller.enabled=false}
 * 整体关闭默认控制器，由接入方自有控制器替代（基座同款开关语义）。
 * 匿名回调入口 /mp/callback/** 无登录态注解，经网关白名单放行（见工程 README）。
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "mse.service.mp.controller", name = "enabled", matchIfMissing = true)
@ComponentScan(basePackages = "vip.mseapp.mp.controller")
public class MpControllerAutoConfiguration {
}
