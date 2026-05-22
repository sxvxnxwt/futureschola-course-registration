package com.futureschole.courseregistration.dto;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.enums.ClassStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ClassDetailResponse(
        Long id,
        String title,
        String description,
        Integer price,
        Integer capacity,
        long enrolledCount,
        ClassStatus status,
        Long creatorId,
        LocalDate startDate,
        LocalDate endDate,
        LocalDateTime createdAt
) {
    public static ClassDetailResponse of(Class clazz, long enrolledCount) {
        return new ClassDetailResponse(
                clazz.getId(),
                clazz.getTitle(),
                clazz.getDescription(),
                clazz.getPrice(),
                clazz.getCapacity(),
                enrolledCount,
                clazz.getStatus(),
                clazz.getCreator().getId(),
                clazz.getStartDate(),
                clazz.getEndDate(),
                clazz.getCreatedAt()
        );
    }
}
