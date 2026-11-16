// ConsultantDetailWithReviewsDTO.java
package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ConsultantDetailWithReviewsDTO {
    private Long consultantId;
    private Long userId;
    private String name;
    private String email;
    private String photoUrl;
    private String skill;
    private String experience;
    private String currentStatus;
    private Long departmentId;
    private String departmentName;
    private Double averageRating;
    private Integer totalReviews;
    private List<ConsultantRatingResponseDTO> reviews;
}