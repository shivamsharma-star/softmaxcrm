package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.SalaryPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface SalaryPaymentRepository extends JpaRepository<SalaryPayment, Long> {

    List<SalaryPayment> findByUserUserId(Long userId);
    List<SalaryPayment> findByMonth(String month);
    List<SalaryPayment> findByUserUserIdAndMonth(Long userId, String month);

    @Query("SELECT sp FROM SalaryPayment sp WHERE sp.payment.status = 'APPROVED'")
    List<SalaryPayment> findApprovedSalaryPayments();

    @Query("SELECT SUM(CAST(sp.payment.amount AS double)) FROM SalaryPayment sp WHERE sp.user.userId = :userId AND sp.payment.status = 'APPROVED'")
    Double getTotalSalaryPaidByUserId(@Param("userId") Long userId);

    @Query("SELECT sp FROM SalaryPayment sp WHERE sp.user.userId IN (SELECT ed.user.userId FROM EmployeeDetails ed WHERE ed.departmentRef.departmentId = :departmentId)")
    List<SalaryPayment> findSalaryPaymentsByDepartmentId(@Param("departmentId") Long departmentId);

    @Query("SELECT sp FROM SalaryPayment sp WHERE sp.payment.status = 'PENDING'")
    List<SalaryPayment> findPendingSalaryPayments();
}