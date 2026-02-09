package com.doron.shaul.usermanagement.repository;

import com.doron.shaul.usermanagement.model.AuthAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuthAuditLogRepository extends JpaRepository<AuthAuditLog, Long> {
    List<AuthAuditLog> findByUserIdOrderByCreatedAtDesc(Long userId);
}
