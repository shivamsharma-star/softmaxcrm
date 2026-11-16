package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class FileViewResponseDTO {
    private String embedUrl;
    private String downloadUrl;
    private String fileType;
    private String fileName;
    private Long fileSize;
}