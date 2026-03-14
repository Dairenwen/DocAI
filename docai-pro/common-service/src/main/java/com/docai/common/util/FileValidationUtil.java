package com.docai.common.util;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.List;

/**
 * 文件校验的工具类
 */
@Component
public class FileValidationUtil {

    // 文件扩展名支持列表
    private static  final List<String> SUPPORT_FILE_EXTENSIONS = Arrays.asList(
            ".xls", ".xlsx"
    );

    // 文件类型的支持列表
    private static  final List<String> SUPPORT_FILE_TYPE = Arrays.asList(
            "application/vnd.ms-excel", // .xls
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" // .xlsx
    );


    public static String getContentType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf("."));
        switch (extension) {
            case ".xls":
                return "application/vnd.ms-excel";
            case ".xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default:
                return "";
        }
    }

    // 获取文件扩展名
    private static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    // 校验文件格式是否支持
    public static boolean validateFileFormat(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return false;
        }
        // 检查文件扩展名
        String extension = getFileExtension(file.getOriginalFilename());
        if (!SUPPORT_FILE_EXTENSIONS.contains(extension)) {
            return false;
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (!SUPPORT_FILE_TYPE.contains(contentType)) {
            return false;
        }
        return true;
    }

    /**
     * 判断文件大小是否在允许范围内
     * @param file 文件
     * @return 文件大小不超过200MB时返回true
     */
    public static boolean ifOutOfLarge(MultipartFile file) {
        return file.getSize() <= 200 * 1024 * 1024;
    }
}
