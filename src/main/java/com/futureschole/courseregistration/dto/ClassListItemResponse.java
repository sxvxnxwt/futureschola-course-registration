package com.futureschole.courseregistration.dto;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.enums.ClassStatus;

import java.time.LocalDate;

public record ClassListItemResponse(
        Long id,
        String title,
        Integer price,
        Integer capacity,
        ClassStatus status,
        LocalDate startDate,
        LocalDate endDate
) {
    public static ClassListItemResponse from(Class clazz) {
        return new ClassListItemResponse(
                clazz.getId(),
                clazz.getTitle(),
                clazz.getPrice(),
                clazz.getCapacity(),
                clazz.getStatus(),
                clazz.getStartDate(),
                clazz.getEndDate()
        );
    }
}
