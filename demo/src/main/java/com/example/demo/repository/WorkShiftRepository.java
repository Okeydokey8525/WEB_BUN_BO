package com.example.demo.repository;

import com.example.demo.model.WorkShift;
import com.example.demo.model.enums.ShiftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

public interface WorkShiftRepository extends JpaRepository<WorkShift, Long> {
    Optional<WorkShift> findByCashierIdAndStatus(Long cashierId, ShiftStatus status);
    Optional<WorkShift> findByIdAndBranchId(Long id, Long branchId);
    List<WorkShift> findByBranchIdOrderByOpenedAtDesc(Long branchId);
    List<WorkShift> findByBranchIdAndCashierIdOrderByOpenedAtDesc(Long branchId, Long cashierId);
    List<WorkShift> findByBranchIdAndOpenedAtGreaterThanEqualAndOpenedAtLessThanOrderByOpenedAtDescIdDesc(
            Long branchId, LocalDateTime fromInclusive, LocalDateTime toExclusive);
    boolean existsByCashierIdAndStatus(Long cashierId, ShiftStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WorkShift s where s.cashier.id = :cashierId and s.status = :status")
    Optional<WorkShift> findOpenShiftForCashierForUpdate(@Param("cashierId") Long cashierId,
                                                          @Param("status") ShiftStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from WorkShift s where s.id = :id and s.branch.id = :branchId")
    Optional<WorkShift> findForUpdateByIdAndBranchId(@Param("id") Long id, @Param("branchId") Long branchId);
}
