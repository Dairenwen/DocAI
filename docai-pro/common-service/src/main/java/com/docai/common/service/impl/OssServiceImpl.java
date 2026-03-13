package com.docai.common.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.docai.common.config.OssConfig;
import com.docai.common.service.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * oss服务的实现类
 */
@Service
@Slf4j
@ConditionalOnBean(OssConfig.class)
public class OssServiceImpl implements OssService {

    @Autowired
    private OssConfig ossConfig;

    @Autowired
    private OSS ossClient;

    @Override
    public String uploadFile(MultipartFile file, String filePath) {
        // 1.获取完整的文件流
        try {
            InputStream inputStream = file.getInputStream();
            String objectKey = createKey(filePath, file.getOriginalFilename());
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    ossConfig.getBucketName(),
                    objectKey,
                    inputStream
            );
            // 2. 调用oss的客户端直接上传代码
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);
            // 3. 返回上传后的url地址
            return getUrl(objectKey);
        } catch (IOException e) {
            log.error("上传oss失败{}", file.getOriginalFilename());
            throw new RuntimeException(e);
        }
    }

    @Override
    public OSSObject getObject(String objectKey) {
        return ossClient.getObject(ossConfig.getBucketName(), objectKey);
    }

    @Override
    public void deleteFile(String ossKey) {
        ossClient.deleteObject(ossConfig.getBucketName(), ossKey);
    }


    private String createKey(String filePath, String fileName) {
        if (filePath == null || filePath.trim().isEmpty()) {
            return "uploads/" +fileName;
        } else {
            return filePath + fileName;
        }
    }

    private String getUrl(String objectKey) {
        return String.format("https://%s.%s/%s",
                ossConfig.getBucketName(),
                ossConfig.getEndPoint(),
                objectKey
                );
    }
}
