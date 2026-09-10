package com.barriodigital.requests.repository;

import com.barriodigital.requests.entity.Request;
import com.barriodigital.requests.model.RequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {
    List<Request> findByStatus(RequestStatus status);
    List<Request> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}