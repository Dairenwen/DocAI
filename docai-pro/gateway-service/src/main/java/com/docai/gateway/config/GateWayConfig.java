package com.docai.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 网关配置类
 */
@Configuration
public class GateWayConfig {

    /**
     * 自定义路由配置
     * @param builder RouteLocatorBuilder构建器
     * @return  RouteLocator实例对象
     */
    @Bean
    public RouteLocator routeLocator(RouteLocatorBuilder builder) {
        return builder.routes().build();
    }

}
