package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
import com.futureschole.courseregistration.dto.EnrollmentCreateRequest;
import com.futureschole.courseregistration.dto.EnrollmentCreateResponse;
import com.futureschole.courseregistration.exception.CustomException;
import com.futureschole.courseregistration.exception.ErrorCode;
import com.futureschole.courseregistration.repository.ClassRepository;
import com.futureschole.courseregistration.repository.EnrollmentRepository;
import com.futureschole.courseregistration.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private ClassRepository classRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private static final List<EnrollmentStatus> ACTIVE_STATUSES =
            List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED);

    private static Class buildClass(Long classId, ClassStatus status, int capacity) {
        User creator = mock(User.class);
        lenient().when(creator.getId()).thenReturn(99L);
        given(creator.getRole()).willReturn(UserRole.CREATOR);

        Class clazz = Class.create(
                creator,
                "웹 프로그래밍",
                "웹 프로그래밍 기초",
                50_000,
                capacity,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 31)
        );
        setField(clazz, "id", classId);
        setField(clazz, "status", status);
        return clazz;
    }

    private static User buildUserRef(Long userId) {
        User user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
        return user;
    }

    private static void setField(Class clazz, String name, Object value) {
        try {
            Field field = Class.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(clazz, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("수강 신청")
    class Enroll {

        @Test
        @DisplayName("OPEN 강의에 정상 신청하면 PENDING 상태의 Enrollment가 저장된다")
        void enroll_success() {
            // given
            Long userId = 200L;
            Long classId = 1L;
            Class clazz = buildClass(classId, ClassStatus.OPEN, 30);
            User userRef = buildUserRef(userId);

            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));
            given(enrollmentRepository.existsByUser_IdAndClazz_IdAndStatusIn(userId, classId, ACTIVE_STATUSES))
                    .willReturn(false);
            given(enrollmentRepository.countByClazz_IdAndStatusIn(classId, ACTIVE_STATUSES))
                    .willReturn(10L);
            given(userRepository.getReferenceById(userId)).willReturn(userRef);
            given(enrollmentRepository.save(any(Enrollment.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));

            // when
            EnrollmentCreateResponse response = enrollmentService.enroll(userId, new EnrollmentCreateRequest(classId));

            // then
            assertThat(response.classId()).isEqualTo(classId);
            assertThat(response.userId()).isEqualTo(userId);
            assertThat(response.status()).isEqualTo(EnrollmentStatus.PENDING);

            ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
            verify(enrollmentRepository).save(captor.capture());
            Enrollment saved = captor.getValue();
            assertThat(saved.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(saved.getClazz()).isSameAs(clazz);
            assertThat(saved.getUser()).isSameAs(userRef);
        }

        @Test
        @DisplayName("존재하지 않는 classId로 신청하면 CLASS_NOT_FOUND 예외가 발생한다")
        void enroll_classNotFound() {
            // given
            Long classId = 99L;
            given(classRepository.findById(classId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> enrollmentService.enroll(200L, new EnrollmentCreateRequest(classId)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CLASS_NOT_FOUND);

            verify(enrollmentRepository, never()).save(any(Enrollment.class));
        }

        @Test
        @DisplayName("DRAFT 상태 강의에 신청하면 CLASS_NOT_FOUND 예외가 발생한다")
        void enroll_draftClass_returnsNotFound() {
            // given
            Long classId = 1L;
            Class clazz = buildClass(classId, ClassStatus.DRAFT, 30);
            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));

            // when & then
            assertThatThrownBy(() -> enrollmentService.enroll(200L, new EnrollmentCreateRequest(classId)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CLASS_NOT_FOUND);

            verify(enrollmentRepository, never()).save(any(Enrollment.class));
        }

        @Test
        @DisplayName("CLOSED 상태 강의에 신청하면 CLASS_NOT_OPEN 예외가 발생한다")
        void enroll_closedClass_returnsClassNotOpen() {
            // given
            Long classId = 1L;
            Class clazz = buildClass(classId, ClassStatus.CLOSED, 30);
            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));

            // when & then
            assertThatThrownBy(() -> enrollmentService.enroll(200L, new EnrollmentCreateRequest(classId)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CLASS_NOT_OPEN);

            verify(enrollmentRepository, never()).save(any(Enrollment.class));
        }

        @Test
        @DisplayName("현재 신청 인원이 정원 이상이면 CLASS_FULL 예외가 발생한다")
        void enroll_capacityExceeded_returnsClassFull() {
            // given
            Long userId = 200L;
            Long classId = 1L;
            Class clazz = buildClass(classId, ClassStatus.OPEN, 30);

            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));
            given(enrollmentRepository.existsByUser_IdAndClazz_IdAndStatusIn(userId, classId, ACTIVE_STATUSES))
                    .willReturn(false);
            given(enrollmentRepository.countByClazz_IdAndStatusIn(classId, ACTIVE_STATUSES))
                    .willReturn(30L);

            // when & then
            assertThatThrownBy(() -> enrollmentService.enroll(userId, new EnrollmentCreateRequest(classId)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CLASS_FULL);

            verify(enrollmentRepository, never()).save(any(Enrollment.class));
        }

        @Test
        @DisplayName("동일 user의 PENDING 신청이 이미 존재하면 ALREADY_ENROLLED 예외가 발생한다")
        void enroll_existingPending_returnsAlreadyEnrolled() {
            // given
            Long userId = 200L;
            Long classId = 1L;
            Class clazz = buildClass(classId, ClassStatus.OPEN, 30);

            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));
            given(enrollmentRepository.existsByUser_IdAndClazz_IdAndStatusIn(userId, classId, ACTIVE_STATUSES))
                    .willReturn(true);

            // when & then
            assertThatThrownBy(() -> enrollmentService.enroll(userId, new EnrollmentCreateRequest(classId)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ALREADY_ENROLLED);

            verify(enrollmentRepository, never()).save(any(Enrollment.class));
            verify(enrollmentRepository, never()).countByClazz_IdAndStatusIn(anyLong(), anyCollection());
        }

        @Test
        @DisplayName("동일 user의 CONFIRMED 신청이 이미 존재하면 ALREADY_ENROLLED 예외가 발생한다")
        void enroll_existingConfirmed_returnsAlreadyEnrolled() {
            // given
            Long userId = 200L;
            Long classId = 1L;
            Class clazz = buildClass(classId, ClassStatus.OPEN, 30);

            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));
            given(enrollmentRepository.existsByUser_IdAndClazz_IdAndStatusIn(userId, classId, ACTIVE_STATUSES))
                    .willReturn(true);

            // when & then
            assertThatThrownBy(() -> enrollmentService.enroll(userId, new EnrollmentCreateRequest(classId)))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ALREADY_ENROLLED);

            verify(enrollmentRepository, never()).save(any(Enrollment.class));
            verify(enrollmentRepository, never()).countByClazz_IdAndStatusIn(anyLong(), anyCollection());
        }
    }
}
