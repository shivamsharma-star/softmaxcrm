package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class FileUploadRequest {
    private Long projectId;
    private String fileType;
    private MultipartFile file;
}