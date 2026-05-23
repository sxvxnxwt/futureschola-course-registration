package com.futureschole.courseregistration.dto;

import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;

import java.time.LocalDateTime;

public record EnrollmentListItemResponse(
        Long id,
        Long classId,
        String classTitle,
        EnrollmentStatus status,
        LocalDateTime appliedAt,
        LocalDateTime confirmedAt
) {
    public static EnrollmentListItemResponse from(Enrollment e) {
        return new EnrollmentListItemResponse(
                e.getId(),
                e.getClazz().getId(),
                e.getClazz().getTitle(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getConfirmedAt()
        );
    }
}
