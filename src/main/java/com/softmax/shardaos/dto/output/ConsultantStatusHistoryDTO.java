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
public class ConsultantStatusHistoryDTO {
    private Long statusId;
    private String status;
    private String note;
    private LocalDateTime dateTime;
}