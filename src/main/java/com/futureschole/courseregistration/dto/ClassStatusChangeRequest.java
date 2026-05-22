package com.futureschole.courseregistration.dto;

import com.futureschole.courseregistration.domain.enums.ClassStatus;
import jakarta.validation.constraints.NotNull;

public record ClassStatusChangeRequest(
        @NotNull ClassStatus status
) {
}
