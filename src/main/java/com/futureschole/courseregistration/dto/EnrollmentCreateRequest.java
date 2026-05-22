package com.futureschole.courseregistration.dto;

import jakarta.validation.constraints.NotNull;

public record EnrollmentCreateRequest(
        @NotNull Long classId
) {
}
