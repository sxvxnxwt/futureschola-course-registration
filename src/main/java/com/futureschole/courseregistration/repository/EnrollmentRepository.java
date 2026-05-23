package com.futureschole.courseregistration.repository;

import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.dto.EnrollmentListItemResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

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
              AND e.status IN ('PENDING', 'CONFIRMED')
            """)
    int cancelIfActive(
            @Param("id") Long enrollmentId,
            @Param("userId") Long userId,
            @Param("cancelledAt") LocalDateTime cancelledAt
    );

    @Query("""
            SELECT new com.futureschole.courseregistration.dto.EnrollmentListItemResponse(
                e.id, c.id, c.title, e.status, e.createdAt, e.confirmedAt
            )
            FROM Enrollment e
            JOIN e.clazz c
            WHERE e.user.id = :userId
            ORDER BY e.createdAt DESC
            """)
    List<EnrollmentListItemResponse> findMyEnrollments(@Param("userId") Long userId);

    @Query("""
            SELECT new com.futureschole.courseregistration.dto.EnrollmentListItemResponse(
                e.id, c.id, c.title, e.status, e.createdAt, e.confirmedAt
            )
            FROM Enrollment e
            JOIN e.clazz c
            WHERE e.user.id = :userId
              AND e.status = :status
            ORDER BY e.createdAt DESC
            """)
    List<EnrollmentListItemResponse> findMyEnrollmentsByStatus(
            @Param("userId") Long userId,
            @Param("status") EnrollmentStatus status
    );
}
