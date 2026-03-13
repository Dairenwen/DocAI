package com.docai.ai.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.TimeUnit;

// http客户端的配置类
@Configuration
public class HttpClientConfig {

    // http的请求工厂
    @Bean
    public HttpComponentsClientHttpRequestFactory httpRequestFactory() {
        // 连接超时时间、请求超时时间、响应超时时间
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(30, TimeUnit.SECONDS)
                .setConnectionRequestTimeout(120, TimeUnit.SECONDS)
                .setResponseTimeout(120, TimeUnit.SECONDS)
                .build();

        // 创建客户端
        CloseableHttpClient httpClient =
                HttpClients.custom()
                        .setDefaultRequestConfig(requestConfig)
                        .build();
        return new HttpComponentsClientHttpRequestFactory(httpClient);
    }

    @Bean
    public RestTemplate restTemplate(HttpComponentsClientHttpRequestFactory requestFactory) {
        return new RestTemplate(requestFactory);
    }
}
