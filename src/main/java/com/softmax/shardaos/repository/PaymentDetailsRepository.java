package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.PaymentDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface PaymentDetailsRepository extends JpaRepository<PaymentDetails, Long> {

    List<PaymentDetails> findByUserUserId(Long userId);
    List<PaymentDetails> findByType(String type);
    List<PaymentDetails> findByStatus(String status);
    List<PaymentDetails> findByStatusAndType(String status, String type);
    Optional<PaymentDetails> findByTransactionId(String transactionId);
    List<PaymentDetails> findByUserUserIdAndStatus(Long userId, String status);

    @Query("SELECT pd FROM PaymentDetails pd JOIN ProjectPayment pp ON pp.payment.paymentId = pd.paymentId WHERE pp.project.projectId = :projectId")
    List<PaymentDetails> findPaymentsByProjectId(@Param("projectId") Long projectId);

    @Query("SELECT SUM(CAST(pd.amount AS double)) FROM PaymentDetails pd JOIN ProjectPayment pp ON pp.payment.paymentId = pd.paymentId WHERE pp.project.projectId = :projectId AND pd.status = 'APPROVED'")
    Double getTotalApprovedAmountByProjectId(@Param("projectId") Long projectId);

    List<PaymentDetails> findByStatusOrderByCreatedAtDesc(String status);

    long countByStatus(String pending);
}