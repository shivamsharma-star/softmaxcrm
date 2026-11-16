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
public class ConsultantStatusResponseDTO {
    private Long statusId;
    private Long consultantId;
    private String consultantName;
    private String status;
    private String note;
    private LocalDateTime dateTime;
}