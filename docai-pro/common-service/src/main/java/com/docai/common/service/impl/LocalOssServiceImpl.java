package com.docai.common.service.impl;

import com.aliyun.oss.model.OSSObject;
import com.docai.common.config.OssConfig;
import com.docai.common.service.OssService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Local fallback OSS service for development when Aliyun OSS is not configured.
 */
@Service
@ConditionalOnMissingBean(OssConfig.class)
@Slf4j
public class LocalOssServiceImpl implements OssService {

    private static final Path LOCAL_ROOT = Paths.get("data", "local-oss").toAbsolutePath().normalize();

    @Override
    public String uploadFile(MultipartFile file, String filePath) {
        try {
            String originalName = file.getOriginalFilename() == null ? "file.bin" : file.getOriginalFilename();
            originalName = Paths.get(originalName).getFileName().toString();
            originalName = originalName.replace("\\", "_").replace("/", "_").replace(":", "_");
            String safePath = filePath == null ? "" : filePath.replace("..", "");
            Path targetDir = LOCAL_ROOT.resolve(safePath).normalize();
            Files.createDirectories(targetDir);
            Path targetFile = targetDir.resolve(originalName).normalize();
            Files.copy(file.getInputStream(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            String key = LOCAL_ROOT.relativize(targetFile).toString().replace('\\', '/');
            log.warn("Aliyun OSS not configured, file stored locally: {}", targetFile);
            return "local://" + key;
        } catch (IOException e) {
            throw new RuntimeException("Local OSS upload failed", e);
        }
    }

    @Override
    public OSSObject getObject(String objectKey) {
        try {
            Path targetFile = LOCAL_ROOT.resolve(objectKey).normalize();
            byte[] bytes = Files.readAllBytes(targetFile);
            OSSObject obj = new OSSObject();
            obj.setObjectContent(new ByteArrayInputStream(bytes));
            obj.setKey(objectKey);
            return obj;
        } catch (IOException e) {
            throw new RuntimeException("Local OSS object not found: " + objectKey, e);
        }
    }

    @Override
    public void deleteFile(String ossKey) {
        if (ossKey == null || ossKey.isBlank()) {
            log.warn("deleteFile called with empty ossKey, skipping");
            return;
        }
        try {
            Path targetFile = LOCAL_ROOT.resolve(ossKey).normalize();
            // Safety guard: never delete the root OSS directory itself
            if (targetFile.equals(LOCAL_ROOT)) {
                log.warn("deleteFile resolved to LOCAL_ROOT (ossKey={}), skipping to prevent data loss", ossKey);
                return;
            }
            Files.deleteIfExists(targetFile);
        } catch (IOException e) {
            throw new RuntimeException("Local OSS delete failed: " + ossKey, e);
        }
    }
}
