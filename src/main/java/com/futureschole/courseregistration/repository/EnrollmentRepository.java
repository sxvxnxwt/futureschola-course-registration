package com.futureschole.courseregistration.repository;

import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    long countByClazz_IdAndStatusIn(Long classId, Collection<EnrollmentStatus> statuses);

    boolean existsByUser_IdAndClazz_IdAndStatusIn(
            Long userId,
            Long classId,
            Collection<EnrollmentStatus> statuses
    );
}
