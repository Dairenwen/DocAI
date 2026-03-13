package com.docai.common.service;

import com.aliyun.oss.model.OSSObject;
import org.springframework.web.multipart.MultipartFile;

/**
 * 阿里云oss服务接口
 */
public interface OssService {

    /**
     * 上传文件到指定路径
     * @param file 文件本身
     * @param filePath 文件路径
     * @return 可以访问的URL
     */
    String uploadFile(MultipartFile file, String filePath);

    /**
     * 根据oss的key获取对象
     * @param objectKey oss的key
     * @return oss对象
     */
    OSSObject getObject(String objectKey);

    void deleteFile(String ossKey);
}
