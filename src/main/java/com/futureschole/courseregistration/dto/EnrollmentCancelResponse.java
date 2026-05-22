package com.futureschole.courseregistration.dto;

import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;

import java.time.LocalDateTime;

public record EnrollmentCancelResponse(
        Long id,
        EnrollmentStatus status,
        LocalDateTime cancelledAt
) {
    public static EnrollmentCancelResponse from(Enrollment enrollment) {
        return new EnrollmentCancelResponse(
                enrollment.getId(),
                enrollment.getStatus(),
                enrollment.getCancelledAt()
        );
    }
}
