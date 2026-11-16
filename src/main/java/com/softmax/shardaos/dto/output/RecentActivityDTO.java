package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecentActivityDTO {
    private String activityType;
    private String description;
    private String userName;
    private String departmentName;
    private LocalDateTime activityTime;
}