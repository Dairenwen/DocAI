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
        return builder.routes()
            .route("user-service", r -> r.path("/api/v1/users/**")
                .filters(f -> f.stripPrefix(2))
                .uri("http://127.0.0.1:9001"))
            .route("file-service", r -> r.path("/api/v1/files/**")
                .filters(f -> f.stripPrefix(2))
                .uri("http://127.0.0.1:9003"))
            .route("ai-service-ai", r -> r.path("/api/v1/ai/**")
                .filters(f -> f.stripPrefix(2))
                .uri("http://127.0.0.1:9002"))
            .route("ai-service-llm", r -> r.path("/api/v1/llm/**")
                .filters(f -> f.stripPrefix(2))
                .uri("http://127.0.0.1:9002"))
            .route("ai-service-source", r -> r.path("/api/v1/source/**")
                .filters(f -> f.stripPrefix(2))
                .uri("http://127.0.0.1:9002"))
            .route("ai-service-template", r -> r.path("/api/v1/template/**")
                .filters(f -> f.stripPrefix(2))
                .uri("http://127.0.0.1:9002"))
            .build();
    }

}
