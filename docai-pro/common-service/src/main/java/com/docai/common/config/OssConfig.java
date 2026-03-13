package com.docai.common.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun.oss")
@ConditionalOnProperty(prefix = "aliyun.oss", name = "access-key-id")
public class OssConfig {

    /**
     * 访问断点
     */
    private String endPoint;

    /**
     * 秘钥ID
     */
    private String accessKeyId;

    /**
     * 秘钥
     */
    private String accessKeySecret;

    /**
     * 存储空间
     */
    private String bucketName;

    /**
     * 最大文件限制（字节）
     */
    private Long maxFileSize;

    @Bean
    public OSS ossClient() {
        OSS oSSClient = new OSSClientBuilder().build(
                this.endPoint,
                this.accessKeyId,
                this.accessKeySecret
        );
        return oSSClient;
    }
}
