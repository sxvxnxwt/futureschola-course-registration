package com.futureschole.courseregistration.dto;

import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;

import java.time.LocalDateTime;

public record PaymentConfirmResponse(
        Long id,
        EnrollmentStatus status,
        LocalDateTime confirmedAt
) {
    public static PaymentConfirmResponse from(Enrollment enrollment) {
        return new PaymentConfirmResponse(
                enrollment.getId(),
                enrollment.getStatus(),
                enrollment.getConfirmedAt()
        );
    }
}
