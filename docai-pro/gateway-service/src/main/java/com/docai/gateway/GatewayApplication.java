package com.docai.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * 网关服务启动类
 */
@SpringBootApplication(
        exclude = {
                DataSourceAutoConfiguration.class
        }
)
@Slf4j
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class);
        log.info("Gateway Port: 8080");
    }
}
