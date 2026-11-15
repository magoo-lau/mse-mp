package vip.mseapp.boot.mp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 微信公众号管理平台启动器。
 *
 * <p>启动类位于 vip.mseapp.boot.mp，与业务积木包树零重叠——默认扫描不触碰任何积木：
 * 自有积木（mp-service）引入 jar 即经 Spring Boot SPI 自动装配（Controller/Service/Mapper
 * 全部就绪）；基座能力不打包在本服务——鉴权数据源（@SaCheckPermission）/独立库租户路由经
 * starter-bridge 以契约消费 mse-system/mse-tenant，登录与系统管理收敛在基座微服务。
 */
@SpringBootApplication
public class MpBootApplication {

    public static void main(String[] args) {
        SpringApplication.run(MpBootApplication.class, args);
    }
}
