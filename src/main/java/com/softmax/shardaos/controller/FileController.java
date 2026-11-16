package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.FileAccessTokenDTO;
import com.softmax.shardaos.dto.output.FileResponseDTO;
import com.softmax.shardaos.dto.output.FileViewResponseDTO;
import com.softmax.shardaos.service.FileManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/file")
public class FileController {

    @Autowired
    private FileManagementService fileService;

    // ==================== UPLOAD ====================

    @PostMapping("/upload/{projectId}")
    public ResponseEntity<ApiResponse<FileResponseDTO>> uploadFile(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId,
            @RequestParam String fileType,
            @RequestParam("file") MultipartFile file) {
        try {
            FileResponseDTO uploadedFile = fileService.uploadFile(userId, projectId, fileType, file);
            return ResponseEntity.ok(ApiResponse.success("File uploaded successfully", uploadedFile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== GET FILE LIST ====================

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<FileResponseDTO>>> getProjectFiles(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long projectId) {
        try {
            List<FileResponseDTO> files = fileService.getProjectFiles(userId, projectId);
            return ResponseEntity.ok(ApiResponse.success(files));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/my-uploads")
    public ResponseEntity<ApiResponse<List<FileResponseDTO>>> getMyUploads(
            @RequestAttribute("userId") Long userId) {
        try {
            List<FileResponseDTO> files = fileService.getMyUploads(userId);
            return ResponseEntity.ok(ApiResponse.success(files));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    @GetMapping("/department-files")
    public ResponseEntity<ApiResponse<List<FileResponseDTO>>> getDepartmentFiles(
            @RequestAttribute("userId") Long userId) {
        try {
            List<FileResponseDTO> files = fileService.getDepartmentFiles(userId);
            return ResponseEntity.ok(ApiResponse.success(files));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== SECURE ACCESS WITH TOKEN ====================

    @PostMapping("/access-token/{fileId}")
    public ResponseEntity<ApiResponse<FileAccessTokenDTO>> generateAccessToken(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long fileId) {
        try {
            FileAccessTokenDTO token = fileService.generateSecureAccessToken(userId, fileId);
            return ResponseEntity.ok(ApiResponse.success(token));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== VIEW/EMBED FILE (POPUP) ====================

    @GetMapping("/view/{token}")
    public ResponseEntity<Resource> viewFile(@PathVariable String token) {
        try {
            Resource resource = fileService.getFileByToken(token);
            String contentType = fileService.getContentType(token);

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ==================== DOWNLOAD FILE ====================

    @GetMapping("/download/{token}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String token) {
        try {
            Resource resource = fileService.getFileByToken(token);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(403).build();
        }
    }

    // ==================== GET FILE INFO ====================

    @GetMapping("/info/{token}")
    public ResponseEntity<ApiResponse<FileViewResponseDTO>> getFileInfo(@PathVariable String token) {
        try {
            FileViewResponseDTO info = fileService.getFileInfoByToken(token);
            return ResponseEntity.ok(ApiResponse.success(info));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // ==================== DELETE FILE ====================

    @DeleteMapping("/delete/{fileId}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long fileId) {
        try {
            fileService.deleteFile(userId, fileId);
            return ResponseEntity.ok(ApiResponse.success("File deleted successfully", null));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}