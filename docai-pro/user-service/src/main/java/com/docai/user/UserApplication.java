package com.docai.user;

import lombok.extern.slf4j.Slf4j;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 用户服务启动类
 */
@SpringBootApplication
@MapperScan("com.docai.user.mapper")
@ComponentScan(basePackages = {
        "com.docai.user",
        "com.docai.common"
})
@Slf4j
public class UserApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserApplication.class, args);
        log.info("Port: 9001");
    }
}
