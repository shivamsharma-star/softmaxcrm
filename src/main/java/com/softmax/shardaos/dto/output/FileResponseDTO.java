package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class FileResponseDTO {
    private Long fileId;
    private String fileType;
    private String fileLink;
    private Long uploaderId;
    private String uploaderName;
    private LocalDateTime uploadTime;
    private Long departmentId;
    private String departmentName;
}