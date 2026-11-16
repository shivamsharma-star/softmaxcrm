package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.ConsultantStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ConsultantStatusRepository extends JpaRepository<ConsultantStatus, Long> {

    List<ConsultantStatus> findByConsultantConsultantId(Long consultantId);

    // ✅ Get latest status
    @Query("SELECT cs FROM ConsultantStatus cs WHERE cs.consultant.consultantId = :consultantId ORDER BY cs.dateTime DESC")
    List<ConsultantStatus> findLatestByConsultantId(Long consultantId);

    // ✅ Get latest status as Optional
    default Optional<ConsultantStatus> findLatestStatus(Long consultantId) {
        List<ConsultantStatus> list = findLatestByConsultantId(consultantId);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}