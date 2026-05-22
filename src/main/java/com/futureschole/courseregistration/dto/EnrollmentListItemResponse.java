package com.futureschole.courseregistration.dto;

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
}
