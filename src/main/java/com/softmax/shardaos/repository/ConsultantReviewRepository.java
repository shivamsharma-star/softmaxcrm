package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.ConsultantReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ConsultantReviewRepository extends JpaRepository<ConsultantReview, Long> {

    List<ConsultantReview> findByUserUserId(Long userId);

    @Query("SELECT AVG(cr.reviewMarks) FROM ConsultantReview cr WHERE cr.user.userId = :userId")
    Double getAverageRatingByUserId(Long userId);

    @Query("SELECT COUNT(cr) FROM ConsultantReview cr WHERE cr.user.userId = :userId")
    Integer getReviewCountByUserId(Long userId);
}