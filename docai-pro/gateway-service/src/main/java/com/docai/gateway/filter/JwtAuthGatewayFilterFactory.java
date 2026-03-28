package com.docai.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// JWT 网关鉴权过滤器
@Service
@Slf4j
public class JwtAuthGatewayFilterFactory extends AbstractGatewayFilterFactory<JwtAuthGatewayFilterFactory.Config> {

    // 请求路径白名单
    private final List<String> whiteList;

    // 网关令牌
    private String gatewayToken;

    // 缓存对象
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    public JwtAuthGatewayFilterFactory(Environment environment, RedisTemplate<String, String> redisTemplate) {
        super(Config.class);
        this.whiteList = loadWhiteList(environment);
        this.gatewayToken = environment.getProperty("security.gateway.token");
        this.redisTemplate = redisTemplate;
    }

    private List<String> loadWhiteList(Environment environment) {
        List<String> list = new ArrayList<>();
        int index = 0;
        while (true) {
            String path = environment.getProperty("security.whitelist[" + index + "]");
            if (path == null || path.isEmpty()) {
                break;
            }
            list.add(path);
            index++;
        }
        return list;
    }


    @Override
    public GatewayFilter apply(Config config) {

        return ((exchange, chain) -> {
            // 1. 获取请求地址
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();

            log.info("当前请求路径{}", path);
            // 2. 假如地址是白名单，放行就好了
            if (whiteList.contains(path)) {
                request.mutate()
                        .header("X-Gateway-Token", gatewayToken)
                        .build();
                return chain.filter(exchange.mutate().request(request).build());
            }
            // 3. 提取个人令牌，判断用户是否正常，正常放行，否则令牌失效
            String key = "token:" + getToken(request);
            if (redisTemplate.hasKey(key)) {
                request.mutate()
                        .header("X-Gateway-Token", gatewayToken)
                        .build();
                return chain.filter(exchange.mutate().request(request).build());
            } else {
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
                String body = "{\"code\":401,\"message\":\"登录已过期，请重新登录\",\"data\":null}";
                DataBuffer buffer = response.bufferFactory()
                        .wrap(body.getBytes(StandardCharsets.UTF_8));
                return response.writeWith(Mono.just(buffer));
            }

        });
    }

    private String getToken(ServerHttpRequest request) {
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization == null || authorization.isEmpty()) {
            return "";
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return authorization;
    }

    public static class Config {

    }
}
