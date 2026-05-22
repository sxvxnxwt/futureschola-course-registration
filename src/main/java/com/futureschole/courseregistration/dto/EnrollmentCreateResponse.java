package com.futureschole.courseregistration.dto;

import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;

import java.time.LocalDateTime;

public record EnrollmentCreateResponse(
        Long id,
        Long classId,
        Long userId,
        EnrollmentStatus status,
        LocalDateTime appliedAt
) {
    public static EnrollmentCreateResponse from(Enrollment enrollment) {
        return new EnrollmentCreateResponse(
                enrollment.getId(),
                enrollment.getClazz().getId(),
                enrollment.getUser().getId(),
                enrollment.getStatus(),
                enrollment.getCreatedAt()
        );
    }
}
