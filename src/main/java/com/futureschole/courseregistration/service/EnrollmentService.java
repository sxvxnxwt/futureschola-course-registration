package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.dto.EnrollmentCancelResponse;
import com.futureschole.courseregistration.dto.EnrollmentCreateRequest;
import com.futureschole.courseregistration.dto.EnrollmentCreateResponse;
import com.futureschole.courseregistration.dto.EnrollmentListItemResponse;
import com.futureschole.courseregistration.dto.EnrollmentListResponse;
import com.futureschole.courseregistration.dto.PaymentConfirmResponse;
import com.futureschole.courseregistration.exception.CustomException;
import com.futureschole.courseregistration.exception.ErrorCode;
import com.futureschole.courseregistration.repository.ClassRepository;
import com.futureschole.courseregistration.repository.EnrollmentRepository;
import com.futureschole.courseregistration.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    private final EnrollmentRepository enrollmentRepository;
    private final ClassRepository classRepository;
    private final UserRepository userRepository;

    @Transactional
    public EnrollmentCreateResponse enroll(Long userId, EnrollmentCreateRequest request) {
        Long classId = request.classId();

        Class clazz = classRepository.findByIdForUpdate(classId)
                .orElseThrow(() -> new CustomException(ErrorCode.CLASS_NOT_FOUND));

        if (clazz.getStatus() == ClassStatus.DRAFT) {
            throw new CustomException(ErrorCode.CLASS_NOT_FOUND);
        }
        if (clazz.getStatus() == ClassStatus.CLOSED) {
            throw new CustomException(ErrorCode.CLASS_NOT_OPEN);
        }

        if (enrollmentRepository.existsByUser_IdAndClazz_IdAndStatusIn(userId, classId, ACTIVE_STATUSES)) {
            throw new CustomException(ErrorCode.ALREADY_ENROLLED);
        }

        User user = userRepository.getReferenceById(userId);
        Enrollment saved = reserveSeatAndSave(user, clazz);
        return EnrollmentCreateResponse.from(saved);
    }

    @Transactional
    public PaymentConfirmResponse confirmPayment(Long userId, Long enrollmentId) {
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enrollment.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        int updated = enrollmentRepository.confirmIfPending(enrollmentId, userId, LocalDateTime.now());
        if (updated == 0) {
            throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
        }

        Enrollment confirmed = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENROLLMENT_NOT_FOUND));
        return PaymentConfirmResponse.from(confirmed);
    }

    @Transactional
    public EnrollmentCancelResponse cancel(Long userId, Long enrollmentId) {
        // TODO: 결제 확정과의 동시성 처리 — 추후 비관적 락 or 조건부 UPDATE
        Enrollment enrollment = enrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new CustomException(ErrorCode.ENROLLMENT_NOT_FOUND));

        if (!enrollment.getUser().getId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (enrollment.getStatus() == EnrollmentStatus.CANCELLED) {
            throw new CustomException(ErrorCode.INVALID_STATUS_TRANSITION);
        }

        enrollment.cancel(LocalDateTime.now());
        return EnrollmentCancelResponse.from(enrollment);
    }

    @Transactional(readOnly = true)
    public EnrollmentListResponse findMyEnrollments(Long userId, EnrollmentStatus statusFilter) {
        List<EnrollmentListItemResponse> content = (statusFilter == null)
                ? enrollmentRepository.findMyEnrollments(userId)
                : enrollmentRepository.findMyEnrollmentsByStatus(userId, statusFilter);
        return new EnrollmentListResponse(content);
    }

    private Enrollment reserveSeatAndSave(User user, Class clazz) {
        long activeCount = enrollmentRepository.countByClazz_IdAndStatusIn(clazz.getId(), ACTIVE_STATUSES);
        if (activeCount >= clazz.getCapacity()) {
            throw new CustomException(ErrorCode.CLASS_FULL);
        }

        Enrollment enrollment = Enrollment.create(user, clazz);
        return enrollmentRepository.save(enrollment);
    }
}
