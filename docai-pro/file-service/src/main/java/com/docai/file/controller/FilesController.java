package com.docai.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.docai.common.annotation.LogOperation;
import com.docai.common.util.JwtUtil;
import com.docai.common.util.Result;
import com.docai.file.dto.request.ExcelPreviewRequest;
import com.docai.file.dto.request.FileDeleteRequest;
import com.docai.file.dto.request.FileListRequest;
import com.docai.file.dto.request.FileUploadRequest;
import com.docai.file.dto.response.ExcelPreviewResponse;
import com.docai.file.dto.response.FileInfoResponse;
import com.docai.file.dto.response.FileUploadResponse;
import com.docai.file.service.FilesService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 文件服务控制器
 */
@RestController
@RequestMapping("/files")
public class FilesController {


    @Autowired
    private FilesService filesService;

    @Autowired
    private JwtUtil jwtUtil;


    /**
     * 单个文件上传接口
     */
    @PostMapping("/upload/single")
    @LogOperation("文件上传")
    public Result<FileUploadResponse> uploadFile(
            @RequestParam("file")MultipartFile file,
            @RequestHeader(value = "Authorization", required = false)String authorization,
            @Valid FileUploadRequest request
            ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }

        FileUploadResponse response = filesService.upload(file, request, userId);
        return  Result.success("文件上传成功", response);
    }

    // 分页查询当前用户的文件列表
    @GetMapping("/list")
    @LogOperation("文件列表查询")
    public Result<IPage<FileInfoResponse>> list(
            @RequestHeader(value = "Authorization", required = false)String authorization,
            @Valid FileListRequest request
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        request.setUserId(userId);
        IPage<FileInfoResponse> result = filesService.list(request);
        return Result.success("查询成功", result);
    }


    // 下载文件
    @GetMapping("/download")
    @LogOperation("文件下载")
    public void downloadFile(
            @RequestHeader(value = "Authorization", required = false)String authorization,
            @RequestParam("fileId") Long fileId,
            HttpServletResponse response
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            try {
                response.getWriter().write("{\"code\":401,\"message\":\"无效的令牌\"}");
                return;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        filesService.downloadFile(fileId, response, userId);
    }

    // 文件预览
    @GetMapping("/excel/preview/{fileId}")
    @LogOperation("Excel文件预览")
    public Result<ExcelPreviewResponse> previewExcel(
            @PathVariable Long fileId,
            @RequestHeader(value = "Authorization", required = false)String authorization,
            @Valid ExcelPreviewRequest excelPreviewRequest
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        ExcelPreviewResponse response = filesService.previewExcel(
                fileId,
                userId,
                excelPreviewRequest.getPage(),
                excelPreviewRequest.getPageSize(),
                excelPreviewRequest.getSheetIndex()
        );
        return Result.success("excel预览成功", response);
    }

    // 获取文件信息
    @GetMapping("/excel/info/{fileId}")
    @LogOperation("Excel文件信息")
    public Result<ExcelPreviewResponse.ExcelInfo> getExcelInfo(
            @PathVariable Long fileId,
            @RequestHeader(value = "Authorization", required = false)String authorization
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        return Result.success("excel信息获取成功", filesService.getExcelInfo(fileId));
    }

    // 一键复原文件
    @PostMapping("/restore/{fileId}")
    @LogOperation("一键复原excel数据")
    public Result<Boolean> restoreFileData(
            @PathVariable Long fileId,
            @RequestHeader(value = "Authorization", required = false)String authorization
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        return Result.success("文件复原成功", filesService.restoreFileData(fileId, userId));
    }

    // 批量删除文件
    @DeleteMapping("/delete")
    @LogOperation("文件删除")
    public Result<Boolean> deleteFiles(
            @RequestHeader(value = "Authorization", required = false)String authorization,
            @RequestBody @Valid FileDeleteRequest fileDeleteRequest
    ) {
        Long userId = jwtUtil.getUserIdByAuthorization(authorization);
        if (userId == null) {
            return Result.badRequest("无效的令牌");
        }
        return Result.success("删除成功", filesService.deleteFiles(fileDeleteRequest, userId));
    }


}
