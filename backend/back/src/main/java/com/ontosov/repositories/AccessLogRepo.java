package com.ontosov.repositories;

import com.ontosov.models.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccessLogRepo extends JpaRepository<AccessLog, Long> {
    List<AccessLog> findByControllerId(Long controllerId);
    List<AccessLog> findBySubjectId(Long subjectId);
}