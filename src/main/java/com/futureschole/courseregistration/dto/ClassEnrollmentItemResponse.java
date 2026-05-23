package com.futureschole.courseregistration.dto;

import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;

import java.time.LocalDateTime;

public record ClassEnrollmentItemResponse(
        Long enrollmentId,
        Long userId,
        EnrollmentStatus status,
        LocalDateTime appliedAt,
        LocalDateTime confirmedAt
) {
    public static ClassEnrollmentItemResponse from(Enrollment e) {
        return new ClassEnrollmentItemResponse(
                e.getId(),
                e.getUser().getId(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getConfirmedAt()
        );
    }
}
