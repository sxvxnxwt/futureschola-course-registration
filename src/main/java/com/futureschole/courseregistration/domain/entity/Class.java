package com.futureschole.courseregistration.domain.entity;

import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
import com.futureschole.courseregistration.exception.CustomException;
import com.futureschole.courseregistration.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "classes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Class {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column
    private String description;

    @Column(nullable = false)
    private Integer price;

    @Column(nullable = false)
    private Integer capacity;

    @Column(nullable = false)
    private Integer enrollmentCount = 0;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClassStatus status = ClassStatus.DRAFT;

    @CreatedDate
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public static Class create(
            User creator,
            String title,
            String description,
            Integer price,
            Integer capacity,
            LocalDate startDate,
            LocalDate endDate
    ) {
        validate(creator, capacity, startDate, endDate);

        Class clazz = new Class();
        clazz.creator = creator;
        clazz.title = title;
        clazz.description = description;
        clazz.price = price;
        clazz.capacity = capacity;
        clazz.startDate = startDate;
        clazz.endDate = endDate;
        clazz.status = ClassStatus.DRAFT;
        clazz.enrollmentCount = 0;
        return clazz;
    }

    private static void validate(User creator, Integer capacity, LocalDate startDate, LocalDate endDate) {
        if (creator == null || creator.getRole() != UserRole.CREATOR) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        if (capacity == null || capacity <= 0) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
    }
}
