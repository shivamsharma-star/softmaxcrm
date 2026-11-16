package com.softmax.shardaos.dto.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class ConsultantWithRatingDTO {
    private Long consultantId;
    private Long userId;
    private String name;
    private String email;
    private String photoUrl;
    private String skill;
    private String experience;
    private Double averageRating;
    private Integer totalReviews;
}