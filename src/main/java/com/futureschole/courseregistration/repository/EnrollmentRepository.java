package com.futureschole.courseregistration.repository;

import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long countByClazz_IdAndStatusIn(Long classId, Collection<EnrollmentStatus> statuses);

    boolean existsByUser_IdAndClazz_IdAndStatusIn(
            Long userId,
            Long classId,
            Collection<EnrollmentStatus> statuses
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Enrollment e
            SET e.status = 'CONFIRMED',
                e.confirmedAt = :confirmedAt
            WHERE e.id = :id
              AND e.user.id = :userId
              AND e.status = 'PENDING'
            """)
    int confirmIfPending(
            @Param("id") Long enrollmentId,
            @Param("userId") Long userId,
            @Param("confirmedAt") LocalDateTime confirmedAt
    );

    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Enrollment e
            SET e.status = 'CANCELLED',
                e.cancelledAt = :cancelledAt
            WHERE e.id = :id
              AND e.user.id = :userId
              AND e.status = :expectedStatus
            """)
    int cancelIfStatus(
            @Param("id") Long enrollmentId,
            @Param("userId") Long userId,
            @Param("expectedStatus") EnrollmentStatus expectedStatus,
            @Param("cancelledAt") LocalDateTime cancelledAt
    );

    @EntityGraph(attributePaths = "clazz")
    Page<Enrollment> findByUser_Id(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "clazz")
    Page<Enrollment> findByUser_IdAndStatus(
            Long userId,
            EnrollmentStatus status,
            Pageable pageable
    );

    Page<Enrollment> findByClazz_Id(Long classId, Pageable pageable);

    Page<Enrollment> findByClazz_IdAndStatus(
            Long classId,
            EnrollmentStatus status,
            Pageable pageable
    );
}
