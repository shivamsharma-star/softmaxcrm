package com.softmax.shardaos.repository;

import com.softmax.shardaos.models.ClientDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientDetailsRepository extends JpaRepository<ClientDetails, Long> {

    Optional<ClientDetails> findByUserUserId(Long userId);

}