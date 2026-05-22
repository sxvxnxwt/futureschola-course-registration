package com.futureschole.courseregistration.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDate;

public record ClassCreateRequest(
        @NotBlank String title,
        String description,
        @NotNull @PositiveOrZero Integer price,
        @NotNull Integer capacity,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate
) {
}
