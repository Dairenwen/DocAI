package com.docai.common.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 网关令牌验证过滤器
 */
@Component
@Slf4j
@Order(1)
public class GatewayTokenFilter implements Filter {

    /**
     * 网关令牌
     */
    @Value("${security.gateway.token:}")
    private String gatewayToken;

    /**
     * 是否启用网关令牌（网关控制器）
     */
    @Value("${security.gateway.enabled:true}")
    private boolean gatewayCheckEnable;


    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (gatewayCheckEnable) {
            log.info("网关令牌过滤器已经启动");
        } else {
            log.info("允许直接访问后端");
        }
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        // 1. 需要请求request
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        HttpServletResponse httpServletResponse = (HttpServletResponse) servletResponse;
        String path = httpServletRequest.getRequestURI();
        String method = httpServletRequest.getMethod();

        // 2. 网关控制器做判断
        if (!gatewayCheckEnable) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        // 3. 获取网关令牌
        String gatewayToken = httpServletRequest.getHeader("X-Gateway-Token");
        if (gatewayToken == null || gatewayToken.isEmpty()) {
            log.warn("网关令牌为空");
            unAuthorized(httpServletResponse, "禁止访问");
            return;
        }

        if (!gatewayToken.equals(this.gatewayToken)) {
            log.warn("网关令牌错误");
            unAuthorized(httpServletResponse, "禁止访问");
            return;
        }

        // 4. 验证网关令牌
        log.info("网关令牌验证通过:{} {}", method, path);
        filterChain.doFilter(httpServletRequest, httpServletResponse);
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }


    /**
     * 用于处理网关令牌不存在的情况
     * @param response 响应
     * @param message 文案
     */
    private void unAuthorized(HttpServletResponse response, String message) {
        response.setStatus(403);
        response.setContentType("application/json;charset=UTF-8");
        String json = String.format(
                "{\"code\":%d,\"message\":\"%s\"}",
                403,
                message
        );
        try {
            response.getWriter().write(json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
