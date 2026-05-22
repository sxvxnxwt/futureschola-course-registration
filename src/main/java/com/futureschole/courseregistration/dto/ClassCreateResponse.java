package com.futureschole.courseregistration.dto;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.enums.ClassStatus;

import java.time.LocalDateTime;

public record ClassCreateResponse(
        Long id,
        String title,
        ClassStatus status,
        Long creatorId,
        LocalDateTime createdAt
) {
    public static ClassCreateResponse from(Class clazz) {
        return new ClassCreateResponse(
                clazz.getId(),
                clazz.getTitle(),
                clazz.getStatus(),
                clazz.getCreator().getId(),
                clazz.getCreatedAt()
        );
    }
}
