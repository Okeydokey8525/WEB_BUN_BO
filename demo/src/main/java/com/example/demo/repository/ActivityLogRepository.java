package com.example.demo.repository;

import com.example.demo.model.ActivityLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    @Query("""
            select log from ActivityLog log
            where log.branch.id = :branchId
              and (:action is null or log.action = :action)
              and (:username is null or log.username = :username)
              and (:fromInclusive is null or log.timestamp >= :fromInclusive)
              and (:toExclusive is null or log.timestamp < :toExclusive)
            order by log.timestamp desc, log.id desc
            """)
    Page<ActivityLog> findBranchAuditLogs(
            @Param("branchId") Long branchId,
            @Param("action") String action,
            @Param("username") String username,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable);
}
