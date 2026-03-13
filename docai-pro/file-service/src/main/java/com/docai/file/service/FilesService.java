package com.docai.file.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.docai.file.dto.request.FileDeleteRequest;
import com.docai.file.dto.request.FileListRequest;
import com.docai.file.dto.request.FileUploadRequest;
import com.docai.file.dto.response.ExcelPreviewResponse;
import com.docai.file.dto.response.FileInfoResponse;
import com.docai.file.dto.response.FileUploadResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

/**
 * 操作文件的接口
 */
public interface FilesService {


    FileUploadResponse upload(MultipartFile file, FileUploadRequest request, Long userId);

    IPage<FileInfoResponse> list(FileListRequest request);

    void downloadFile(Long fileId, HttpServletResponse response, Long userId);

    ExcelPreviewResponse previewExcel(Long fileId, Long userId, Integer page, Integer pageSize, Integer sheetIndex);

    ExcelPreviewResponse.ExcelInfo getExcelInfo(Long fileId);

    boolean restoreFileData(Long fileId, Long userId);

    Boolean deleteFiles(@Valid FileDeleteRequest fileDeleteRequest, Long userId);
}
