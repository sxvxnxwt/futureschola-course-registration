package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.dto.EnrollmentCreateRequest;
import com.futureschole.courseregistration.dto.EnrollmentCreateResponse;
import com.futureschole.courseregistration.exception.CustomException;
import com.futureschole.courseregistration.exception.ErrorCode;
import com.futureschole.courseregistration.repository.ClassRepository;
import com.futureschole.courseregistration.repository.EnrollmentRepository;
import com.futureschole.courseregistration.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        Class clazz = classRepository.findById(classId)
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

    private Enrollment reserveSeatAndSave(User user, Class clazz) {
        // TODO: 비관적 락 적용 지점 (SELECT ... FOR UPDATE on Class)
        long activeCount = enrollmentRepository.countByClazz_IdAndStatusIn(clazz.getId(), ACTIVE_STATUSES);
        if (activeCount >= clazz.getCapacity()) {
            throw new CustomException(ErrorCode.CLASS_FULL);
        }

        Enrollment enrollment = Enrollment.create(user, clazz);
        return enrollmentRepository.save(enrollment);
    }
}
