package com.futureschole.courseregistration.repository;

import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.dto.EnrollmentListItemResponse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long countByClazz_IdAndStatusIn(Long classId, Collection<EnrollmentStatus> statuses);

    boolean existsByUser_IdAndClazz_IdAndStatusIn(
            Long userId,
            Long classId,
            Collection<EnrollmentStatus> statuses
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
