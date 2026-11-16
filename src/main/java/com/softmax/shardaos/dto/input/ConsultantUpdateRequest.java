package com.softmax.shardaos.dto.input;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class ConsultantUpdateRequest {
    private Double marks10;
    private Double marks12;
    private Double graduationMarks;
    private String stream;
    private String experience;
    private String skill;
    private String skillType;
    private MultipartFile resume;
}