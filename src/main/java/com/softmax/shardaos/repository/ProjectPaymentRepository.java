package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.PaymentDetails;
import com.softmax.shardaos.models.ProjectPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProjectPaymentRepository extends JpaRepository<ProjectPayment, Long> {

    List<ProjectPayment> findByProjectProjectId(Long projectId);

    // Add this method - find by payment
    Optional<ProjectPayment> findByPayment(PaymentDetails payment);

    // Or use this if the above doesn't work
    Optional<ProjectPayment> findByPayment_PaymentId(Long paymentId);

    @Query("SELECT pp FROM ProjectPayment pp WHERE pp.project.projectId = :projectId AND pp.payment.status = :status")
    List<ProjectPayment> findByProjectIdAndPaymentStatus(@Param("projectId") Long projectId, @Param("status") String status);

    @Query("SELECT SUM(CAST(pp.payment.amount AS double)) FROM ProjectPayment pp WHERE pp.project.projectId = :projectId AND pp.payment.status = 'APPROVED'")
    Double getTotalPaidAmountByProjectId(@Param("projectId") Long projectId);

    boolean existsByProjectProjectIdAndPaymentStatus(Long projectId, String status);
}