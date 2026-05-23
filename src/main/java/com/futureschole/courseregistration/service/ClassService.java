package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassListStatusFilter;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.dto.ClassCreateRequest;
import com.futureschole.courseregistration.dto.ClassCreateResponse;
import com.futureschole.courseregistration.dto.ClassDetailResponse;
import com.futureschole.courseregistration.dto.ClassListItemResponse;
import com.futureschole.courseregistration.dto.ClassStatusChangeRequest;
import com.futureschole.courseregistration.dto.ClassStatusChangeResponse;
import com.futureschole.courseregistration.dto.PageResponse;
import com.futureschole.courseregistration.exception.CustomException;
import com.futureschole.courseregistration.exception.ErrorCode;
import com.futureschole.courseregistration.repository.ClassRepository;
import com.futureschole.courseregistration.repository.EnrollmentRepository;
import com.futureschole.courseregistration.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassService {

    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final EnrollmentRepository enrollmentRepository;

    @Transactional
    public ClassCreateResponse createClass(Long userId, ClassCreateRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Class clazz = Class.create(
                creator,
                request.title(),
                request.description(),
                request.price(),
                request.capacity(),
                request.startDate(),
                request.endDate()
        );

        Class saved = classRepository.save(clazz);
        return ClassCreateResponse.from(saved);
    }

    @Transactional
    public ClassStatusChangeResponse changeStatus(Long userId, Long classId, ClassStatusChangeRequest request) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLASS_NOT_FOUND));

        if (!clazz.isOwnedBy(userId)) {
            throw new CustomException(ErrorCode.CLASS_ACCESS_DENIED);
        }

        clazz.changeStatus(request.status());
        return ClassStatusChangeResponse.from(clazz);
    }

    @Transactional(readOnly = true)
    public PageResponse<ClassListItemResponse> getClasses(ClassListStatusFilter statusFilter, Pageable pageable) {
        List<ClassStatus> statuses = (statusFilter == null)
                ? List.of(ClassStatus.OPEN, ClassStatus.CLOSED)
                : List.of(statusFilter.toClassStatus());

        Page<ClassListItemResponse> page = classRepository
                .findAllByStatusIn(statuses, pageable)
                .map(ClassListItemResponse::from);

        return PageResponse.from(page);
    }

    @Transactional(readOnly = true)
    public ClassDetailResponse getClassDetail(Long classId) {
        Class clazz = classRepository.findById(classId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLASS_NOT_FOUND));

        if (clazz.getStatus() == ClassStatus.DRAFT) {
            throw new CustomException(ErrorCode.CLASS_NOT_FOUND);
        }

        long enrolledCount = enrollmentRepository.countByClazz_IdAndStatusIn(
                classId,
                List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)
        );

        return ClassDetailResponse.of(clazz, enrolledCount);
    }
}
