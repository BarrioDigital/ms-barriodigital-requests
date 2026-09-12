package com.barriodigital.requests.repository;

import com.barriodigital.requests.model.RequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<RequestEntity, Long> {
}