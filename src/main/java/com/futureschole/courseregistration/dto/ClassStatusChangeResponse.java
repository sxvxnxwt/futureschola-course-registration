package com.futureschole.courseregistration.dto;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.enums.ClassStatus;

import java.time.LocalDateTime;

public record ClassStatusChangeResponse(
        Long id,
        ClassStatus status,
        LocalDateTime updatedAt
) {
    public static ClassStatusChangeResponse from(Class clazz) {
        return new ClassStatusChangeResponse(
                clazz.getId(),
                clazz.getStatus(),
                clazz.getUpdatedAt()
        );
    }
}
