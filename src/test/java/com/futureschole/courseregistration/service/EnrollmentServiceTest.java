package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
import com.futureschole.courseregistration.dto.ClassEnrollmentItemResponse;
import com.futureschole.courseregistration.dto.EnrollmentCancelResponse;
import com.futureschole.courseregistration.dto.EnrollmentCreateRequest;
import com.futureschole.courseregistration.dto.EnrollmentCreateResponse;
import com.futureschole.courseregistration.dto.EnrollmentListItemResponse;
import com.futureschole.courseregistration.dto.PageResponse;
import com.futureschole.courseregistration.dto.PaymentConfirmResponse;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private Clock clock;

    @InjectMocks
    private EnrollmentService enrollmentService;

    private static final ZoneId TEST_ZONE = ZoneId.systemDefault();

    private void mockClockNow(LocalDateTime now) {
        lenient().when(clock.instant()).thenReturn(now.atZone(TEST_ZONE).toInstant());
        lenient().when(clock.getZone()).thenReturn(TEST_ZONE);
    }

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

    private static Enrollment buildEnrollment(Long id, User user, Class clazz, EnrollmentStatus status) {
        Enrollment enrollment = Enrollment.create(user, clazz);
        setEnrollmentField(enrollment, "id", id);
        setEnrollmentField(enrollment, "status", status);
        return enrollment;
    }

    private static void setEnrollmentField(Enrollment enrollment, String name, Object value) {
        try {
            Field field = Enrollment.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(enrollment, value);
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

    @Nested
    @DisplayName("결제 확정")
    class PaymentConfirm {

        @Test
        @DisplayName("PENDING 상태의 본인 enrollment를 결제 확정하면 CONFIRMED로 전이되고 confirmedAt이 기록된다")
        void confirmPayment_success() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            Enrollment pending = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.PENDING);
            Enrollment confirmed = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CONFIRMED);
            setEnrollmentField(confirmed, "confirmedAt", LocalDateTime.of(2026, 5, 22, 10, 0));

            given(enrollmentRepository.findById(enrollmentId))
                    .willReturn(Optional.of(pending))
                    .willReturn(Optional.of(confirmed));
            given(enrollmentRepository.confirmIfPending(eq(enrollmentId), eq(userId), any(LocalDateTime.class)))
                    .willReturn(1);

            // when
            PaymentConfirmResponse response = enrollmentService.confirmPayment(userId, enrollmentId);

            // then
            assertThat(response.id()).isEqualTo(enrollmentId);
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CONFIRMED);
            assertThat(response.confirmedAt()).isNotNull();
        }

        @Test
        @DisplayName("다른 사용자의 enrollment에 결제 시도하면 FORBIDDEN 예외가 발생한다")
        void confirmPayment_otherUser_returnsForbidden() {
            // given
            Long ownerId = 200L;
            Long requesterId = 201L;
            Long enrollmentId = 500L;
            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User owner = buildUserRef(ownerId);
            Enrollment enrollment = buildEnrollment(enrollmentId, owner, clazz, EnrollmentStatus.PENDING);

            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when & then
            assertThatThrownBy(() -> enrollmentService.confirmPayment(requesterId, enrollmentId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(enrollment.getConfirmedAt()).isNull();
            verify(enrollmentRepository, never())
                    .confirmIfPending(anyLong(), anyLong(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("이미 CONFIRMED 상태인 enrollment에 결제 시도하면 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void confirmPayment_alreadyConfirmed_returnsInvalidStatusTransition() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            Enrollment enrollment = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CONFIRMED);

            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));
            given(enrollmentRepository.confirmIfPending(eq(enrollmentId), eq(userId), any(LocalDateTime.class)))
                    .willReturn(0);

            // when & then
            assertThatThrownBy(() -> enrollmentService.confirmPayment(userId, enrollmentId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("CANCELLED 상태인 enrollment에 결제 시도하면 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void confirmPayment_cancelled_returnsInvalidStatusTransition() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            Enrollment enrollment = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CANCELLED);

            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));
            given(enrollmentRepository.confirmIfPending(eq(enrollmentId), eq(userId), any(LocalDateTime.class)))
                    .willReturn(0);

            // when & then
            assertThatThrownBy(() -> enrollmentService.confirmPayment(userId, enrollmentId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("존재하지 않는 enrollmentId로 결제 시도하면 ENROLLMENT_NOT_FOUND 예외가 발생한다")
        void confirmPayment_notFound_returnsEnrollmentNotFound() {
            // given
            Long enrollmentId = 999L;
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> enrollmentService.confirmPayment(200L, enrollmentId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND);

            verify(enrollmentRepository, never())
                    .confirmIfPending(anyLong(), anyLong(), any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("수강 취소")
    class Cancel {

        @Test
        @DisplayName("PENDING 상태의 본인 enrollment를 취소하면 CANCELLED로 전이되고 cancelledAt이 기록된다")
        void cancel_fromPending_success() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            mockClockNow(now);

            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            Enrollment pending = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.PENDING);
            Enrollment cancelled = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CANCELLED);
            setEnrollmentField(cancelled, "cancelledAt", now);

            given(enrollmentRepository.findById(enrollmentId))
                    .willReturn(Optional.of(pending))
                    .willReturn(Optional.of(cancelled));
            given(enrollmentRepository.cancelIfActive(eq(enrollmentId), eq(userId), any(LocalDateTime.class)))
                    .willReturn(1);

            // when
            EnrollmentCancelResponse response = enrollmentService.cancel(userId, enrollmentId);

            // then
            assertThat(response.id()).isEqualTo(enrollmentId);
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(response.cancelledAt()).isNotNull();
        }

        @Test
        @DisplayName("CONFIRMED 상태의 본인 enrollment를 취소하면 CANCELLED로 전이되며 confirmedAt은 보존된다")
        void cancel_fromConfirmed_success() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            mockClockNow(now);

            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            LocalDateTime confirmedAt = now.minusDays(1);

            Enrollment confirmed = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CONFIRMED);
            setEnrollmentField(confirmed, "confirmedAt", confirmedAt);

            Enrollment cancelled = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CANCELLED);
            setEnrollmentField(cancelled, "confirmedAt", confirmedAt);
            setEnrollmentField(cancelled, "cancelledAt", now);

            given(enrollmentRepository.findById(enrollmentId))
                    .willReturn(Optional.of(confirmed))
                    .willReturn(Optional.of(cancelled));
            given(enrollmentRepository.cancelIfActive(eq(enrollmentId), eq(userId), any(LocalDateTime.class)))
                    .willReturn(1);

            // when
            EnrollmentCancelResponse response = enrollmentService.cancel(userId, enrollmentId);

            // then
            assertThat(response.id()).isEqualTo(enrollmentId);
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(response.cancelledAt()).isNotNull();
            assertThat(cancelled.getConfirmedAt()).isEqualTo(confirmedAt);
        }

        @Test
        @DisplayName("이미 CANCELLED 상태인 enrollment를 다시 취소하면 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void cancel_alreadyCancelled_returnsInvalidStatusTransition() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            mockClockNow(now);

            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            Enrollment enrollment = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CANCELLED);

            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));
            given(enrollmentRepository.cancelIfActive(eq(enrollmentId), eq(userId), any(LocalDateTime.class)))
                    .willReturn(0);

            // when & then
            assertThatThrownBy(() -> enrollmentService.cancel(userId, enrollmentId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
        }

        @Test
        @DisplayName("PENDING 상태는 confirmedAt이 null이어도 취소에 성공한다")
        void cancel_pendingWithNullConfirmedAt_success() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            mockClockNow(now);

            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            Enrollment pending = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.PENDING);
            assertThat(pending.getConfirmedAt()).isNull();

            Enrollment cancelled = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CANCELLED);
            setEnrollmentField(cancelled, "cancelledAt", now);

            given(enrollmentRepository.findById(enrollmentId))
                    .willReturn(Optional.of(pending))
                    .willReturn(Optional.of(cancelled));
            given(enrollmentRepository.cancelIfActive(eq(enrollmentId), eq(userId), any(LocalDateTime.class)))
                    .willReturn(1);

            // when
            EnrollmentCancelResponse response = enrollmentService.cancel(userId, enrollmentId);

            // then
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(response.cancelledAt()).isNotNull();
        }

        @Test
        @DisplayName("CONFIRMED 상태에서 confirmedAt이 now-6일23시간이면 취소에 성공한다")
        void cancel_confirmedWithin7Days_success() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            mockClockNow(now);

            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            LocalDateTime confirmedAt = now.minusDays(6).minusHours(23);

            Enrollment confirmed = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CONFIRMED);
            setEnrollmentField(confirmed, "confirmedAt", confirmedAt);

            Enrollment cancelled = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CANCELLED);
            setEnrollmentField(cancelled, "confirmedAt", confirmedAt);
            setEnrollmentField(cancelled, "cancelledAt", now);

            given(enrollmentRepository.findById(enrollmentId))
                    .willReturn(Optional.of(confirmed))
                    .willReturn(Optional.of(cancelled));
            given(enrollmentRepository.cancelIfActive(eq(enrollmentId), eq(userId), any(LocalDateTime.class)))
                    .willReturn(1);

            // when
            EnrollmentCancelResponse response = enrollmentService.cancel(userId, enrollmentId);

            // then
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("CONFIRMED 상태에서 confirmedAt이 now-7일1초이면 CANCEL_PERIOD_EXPIRED 예외가 발생하고 cancelIfActive는 호출되지 않는다")
        void cancel_confirmedExpired_returnsCancelPeriodExpired() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            mockClockNow(now);

            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            LocalDateTime confirmedAt = now.minusDays(7).minusSeconds(1);

            Enrollment confirmed = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CONFIRMED);
            setEnrollmentField(confirmed, "confirmedAt", confirmedAt);

            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(confirmed));

            // when & then
            assertThatThrownBy(() -> enrollmentService.cancel(userId, enrollmentId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CANCEL_PERIOD_EXPIRED);

            verify(enrollmentRepository, never())
                    .cancelIfActive(anyLong(), anyLong(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("CONFIRMED 상태에서 confirmedAt이 정확히 now-7일인 경계값에서도 취소에 성공한다 (포함 정책)")
        void cancel_confirmedExactly7Days_success() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            mockClockNow(now);

            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            LocalDateTime confirmedAt = now.minusDays(7);

            Enrollment confirmed = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CONFIRMED);
            setEnrollmentField(confirmed, "confirmedAt", confirmedAt);

            Enrollment cancelled = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CANCELLED);
            setEnrollmentField(cancelled, "confirmedAt", confirmedAt);
            setEnrollmentField(cancelled, "cancelledAt", now);

            given(enrollmentRepository.findById(enrollmentId))
                    .willReturn(Optional.of(confirmed))
                    .willReturn(Optional.of(cancelled));
            given(enrollmentRepository.cancelIfActive(eq(enrollmentId), eq(userId), any(LocalDateTime.class)))
                    .willReturn(1);

            // when
            EnrollmentCancelResponse response = enrollmentService.cancel(userId, enrollmentId);

            // then
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
        }

        @Test
        @DisplayName("존재하지 않는 enrollmentId로 취소 시도하면 ENROLLMENT_NOT_FOUND 예외가 발생한다")
        void cancel_notFound_returnsEnrollmentNotFound() {
            // given
            Long enrollmentId = 999L;
            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> enrollmentService.cancel(200L, enrollmentId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.ENROLLMENT_NOT_FOUND);

            verify(enrollmentRepository, never())
                    .cancelIfActive(anyLong(), anyLong(), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("다른 사용자의 enrollment에 취소 시도하면 FORBIDDEN 예외가 발생한다")
        void cancel_otherUser_returnsForbidden() {
            // given
            Long ownerId = 200L;
            Long requesterId = 201L;
            Long enrollmentId = 500L;
            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User owner = buildUserRef(ownerId);
            Enrollment enrollment = buildEnrollment(enrollmentId, owner, clazz, EnrollmentStatus.PENDING);

            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when & then
            assertThatThrownBy(() -> enrollmentService.cancel(requesterId, enrollmentId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.FORBIDDEN);

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
            assertThat(enrollment.getCancelledAt()).isNull();
            verify(enrollmentRepository, never())
                    .cancelIfActive(anyLong(), anyLong(), any(LocalDateTime.class));
        }
    }

    @Nested
    @DisplayName("내 수강 신청 목록 조회")
    class FindMyEnrollments {

        private static final Pageable DEFAULT_PAGEABLE =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));

        @Test
        @DisplayName("status 필터가 없으면 본인의 모든 상태 신청 내역이 반환된다")
        void findMyEnrollments_withoutStatusFilter_returnsAll() {
            // given
            Long userId = 200L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            User user = buildUserRef(userId);
            Class class1 = buildClass(1L, ClassStatus.OPEN, 30);
            Class class2 = buildClass(2L, ClassStatus.OPEN, 30);
            Class class3 = buildClass(3L, ClassStatus.OPEN, 30);

            Enrollment cancelled = buildEnrollment(502L, user, class2, EnrollmentStatus.CANCELLED);
            setEnrollmentField(cancelled, "createdAt", now.minusHours(1));

            Enrollment confirmed = buildEnrollment(501L, user, class1, EnrollmentStatus.CONFIRMED);
            setEnrollmentField(confirmed, "createdAt", now.minusHours(2));
            setEnrollmentField(confirmed, "confirmedAt", now.minusMinutes(30));

            Enrollment pending = buildEnrollment(500L, user, class3, EnrollmentStatus.PENDING);
            setEnrollmentField(pending, "createdAt", now.minusHours(3));

            List<Enrollment> repoResult = List.of(cancelled, confirmed, pending);
            given(enrollmentRepository.findByUser_Id(eq(userId), any(Pageable.class)))
                    .willReturn(new PageImpl<>(repoResult, DEFAULT_PAGEABLE, repoResult.size()));

            // when
            PageResponse<EnrollmentListItemResponse> response =
                    enrollmentService.findMyEnrollments(userId, null, DEFAULT_PAGEABLE);

            // then
            assertThat(response.content()).hasSize(3);
            assertThat(response.content())
                    .extracting(EnrollmentListItemResponse::status)
                    .containsExactly(
                            EnrollmentStatus.CANCELLED,
                            EnrollmentStatus.CONFIRMED,
                            EnrollmentStatus.PENDING
                    );
            assertThat(response.content())
                    .extracting(EnrollmentListItemResponse::id, EnrollmentListItemResponse::classId)
                    .containsExactly(
                            tuple(502L, 2L),
                            tuple(501L, 1L),
                            tuple(500L, 3L)
                    );
            verify(enrollmentRepository, never())
                    .findByUser_IdAndStatus(anyLong(), any(EnrollmentStatus.class), any(Pageable.class));
        }

        @Test
        @DisplayName("status=CONFIRMED 필터 시 PENDING 신청은 제외되고 CONFIRMED 건만 반환된다")
        void findMyEnrollments_withConfirmedFilter_excludesPending() {
            // given
            Long userId = 200L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            User user = buildUserRef(userId);
            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);

            Enrollment confirmed = buildEnrollment(501L, user, clazz, EnrollmentStatus.CONFIRMED);
            setEnrollmentField(confirmed, "createdAt", now.minusHours(2));
            setEnrollmentField(confirmed, "confirmedAt", now.minusMinutes(30));

            given(enrollmentRepository.findByUser_IdAndStatus(
                    eq(userId), eq(EnrollmentStatus.CONFIRMED), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(confirmed), DEFAULT_PAGEABLE, 1));

            // when
            PageResponse<EnrollmentListItemResponse> response =
                    enrollmentService.findMyEnrollments(userId, EnrollmentStatus.CONFIRMED, DEFAULT_PAGEABLE);

            // then
            assertThat(response.content()).hasSize(1);
            assertThat(response.content())
                    .extracting(EnrollmentListItemResponse::status)
                    .containsExactly(EnrollmentStatus.CONFIRMED);
            assertThat(response.content())
                    .extracting(EnrollmentListItemResponse::status)
                    .doesNotContain(EnrollmentStatus.PENDING);
            verify(enrollmentRepository, never())
                    .findByUser_Id(anyLong(), any(Pageable.class));
        }

        @Test
        @DisplayName("status 필터가 없을 때 전달된 Pageable이 Repository로 그대로 전파된다")
        void findMyEnrollments_passesPageableToRepository() {
            // given
            Long userId = 200L;
            Pageable pageable = PageRequest.of(3, 5, Sort.by(Sort.Direction.ASC, "id"));
            given(enrollmentRepository.findByUser_Id(eq(userId), any(Pageable.class)))
                    .willReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

            // when
            enrollmentService.findMyEnrollments(userId, null, pageable);

            // then
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(enrollmentRepository).findByUser_Id(eq(userId), captor.capture());
            Pageable captured = captor.getValue();
            assertThat(captured.getPageNumber()).isEqualTo(3);
            assertThat(captured.getPageSize()).isEqualTo(5);
            assertThat(captured.getSort().getOrderFor("id"))
                    .isNotNull()
                    .satisfies(order -> assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC));
        }

        @Test
        @DisplayName("status 필터가 있을 때도 전달된 Pageable이 Repository로 그대로 전파된다")
        void findMyEnrollments_passesPageableToRepository_withStatusFilter() {
            // given
            Long userId = 200L;
            Pageable pageable = PageRequest.of(1, 50, Sort.by(Sort.Direction.DESC, "id"));
            given(enrollmentRepository.findByUser_IdAndStatus(
                    eq(userId), eq(EnrollmentStatus.PENDING), any(Pageable.class)))
                    .willReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

            // when
            enrollmentService.findMyEnrollments(userId, EnrollmentStatus.PENDING, pageable);

            // then
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(enrollmentRepository).findByUser_IdAndStatus(
                    eq(userId), eq(EnrollmentStatus.PENDING), captor.capture());
            Pageable captured = captor.getValue();
            assertThat(captured.getPageNumber()).isEqualTo(1);
            assertThat(captured.getPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("Repository가 반환한 Page는 PageResponse의 모든 필드로 정확히 매핑된다")
        void findMyEnrollments_mapsPageMetadataIntoPageResponse() {
            // given
            Long userId = 200L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            User user = buildUserRef(userId);
            Class class1 = buildClass(3L, ClassStatus.OPEN, 30);
            Class class2 = buildClass(1L, ClassStatus.OPEN, 30);

            Enrollment pending = buildEnrollment(500L, user, class1, EnrollmentStatus.PENDING);
            setEnrollmentField(pending, "createdAt", now.minusHours(3));

            Enrollment confirmed = buildEnrollment(501L, user, class2, EnrollmentStatus.CONFIRMED);
            setEnrollmentField(confirmed, "createdAt", now.minusHours(2));
            setEnrollmentField(confirmed, "confirmedAt", now.minusMinutes(30));

            Pageable pageable = PageRequest.of(2, 2, Sort.by(Sort.Direction.DESC, "id"));
            Page<Enrollment> page = new PageImpl<>(List.of(pending, confirmed), pageable, 7);
            given(enrollmentRepository.findByUser_Id(eq(userId), any(Pageable.class)))
                    .willReturn(page);

            // when
            PageResponse<EnrollmentListItemResponse> response =
                    enrollmentService.findMyEnrollments(userId, null, pageable);

            // then
            assertThat(response.content()).hasSize(2);
            assertThat(response.page()).isEqualTo(2);
            assertThat(response.size()).isEqualTo(2);
            assertThat(response.totalElements()).isEqualTo(7);
            assertThat(response.totalPages()).isEqualTo(4);
            assertThat(response.last()).isFalse();
        }

        @Test
        @DisplayName("빈 결과일 때 content는 빈 리스트, totalElements는 0이 된다")
        void findMyEnrollments_emptyPage() {
            // given
            Long userId = 200L;
            given(enrollmentRepository.findByUser_Id(eq(userId), any(Pageable.class)))
                    .willReturn(new PageImpl<>(Collections.emptyList(), DEFAULT_PAGEABLE, 0));

            // when
            PageResponse<EnrollmentListItemResponse> response =
                    enrollmentService.findMyEnrollments(userId, null, DEFAULT_PAGEABLE);

            // then
            assertThat(response.content()).isEmpty();
            assertThat(response.totalElements()).isZero();
            assertThat(response.totalPages()).isZero();
            assertThat(response.last()).isTrue();
        }
    }

    @Nested
    @DisplayName("강의별 수강생 목록 조회")
    class FindClassEnrollments {

        private static final Long CREATOR_ID = 99L;
        private static final Pageable DEFAULT_PAGEABLE =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));

        @Test
        @DisplayName("본인 소유 강의에서 status 필터가 없으면 전체 상태의 수강 신청이 반환된다")
        void findClassEnrollments_withoutStatusFilter_returnsAllStatuses() {
            // given
            Long classId = 1L;
            Class clazz = buildClass(classId, ClassStatus.OPEN, 30);

            User userA = buildUserRef(300L);
            User userB = buildUserRef(301L);
            User userC = buildUserRef(302L);

            Enrollment pending = buildEnrollment(500L, userA, clazz, EnrollmentStatus.PENDING);
            Enrollment confirmed = buildEnrollment(501L, userB, clazz, EnrollmentStatus.CONFIRMED);
            Enrollment cancelled = buildEnrollment(502L, userC, clazz, EnrollmentStatus.CANCELLED);

            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));
            given(enrollmentRepository.findByClazz_Id(eq(classId), any(Pageable.class)))
                    .willReturn(new PageImpl<>(
                            List.of(cancelled, confirmed, pending),
                            DEFAULT_PAGEABLE,
                            3
                    ));

            // when
            PageResponse<ClassEnrollmentItemResponse> response =
                    enrollmentService.findClassEnrollments(CREATOR_ID, classId, null, DEFAULT_PAGEABLE);

            // then
            assertThat(response.content()).hasSize(3);
            assertThat(response.content())
                    .extracting(ClassEnrollmentItemResponse::status)
                    .containsExactly(
                            EnrollmentStatus.CANCELLED,
                            EnrollmentStatus.CONFIRMED,
                            EnrollmentStatus.PENDING
                    );
            assertThat(response.content())
                    .extracting(ClassEnrollmentItemResponse::enrollmentId, ClassEnrollmentItemResponse::userId)
                    .containsExactly(
                            tuple(502L, 302L),
                            tuple(501L, 301L),
                            tuple(500L, 300L)
                    );
            verify(enrollmentRepository, never())
                    .findByClazz_IdAndStatus(anyLong(), any(EnrollmentStatus.class), any(Pageable.class));
        }

        @Test
        @DisplayName("본인 소유 강의에서 status 지정 시 동일 status/Pageable이 Repository에 그대로 전달된다")
        void findClassEnrollments_withStatusFilter_passesArgsToRepository() {
            // given
            Long classId = 1L;
            Class clazz = buildClass(classId, ClassStatus.OPEN, 30);
            Pageable pageable = PageRequest.of(2, 10, Sort.by(Sort.Direction.ASC, "id"));

            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));
            given(enrollmentRepository.findByClazz_IdAndStatus(
                    eq(classId), eq(EnrollmentStatus.CONFIRMED), any(Pageable.class)))
                    .willReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

            // when
            enrollmentService.findClassEnrollments(
                    CREATOR_ID, classId, EnrollmentStatus.CONFIRMED, pageable);

            // then
            ArgumentCaptor<EnrollmentStatus> statusCaptor = ArgumentCaptor.forClass(EnrollmentStatus.class);
            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(enrollmentRepository).findByClazz_IdAndStatus(
                    eq(classId), statusCaptor.capture(), pageableCaptor.capture());
            assertThat(statusCaptor.getValue()).isEqualTo(EnrollmentStatus.CONFIRMED);
            Pageable captured = pageableCaptor.getValue();
            assertThat(captured.getPageNumber()).isEqualTo(2);
            assertThat(captured.getPageSize()).isEqualTo(10);
            assertThat(captured.getSort().getOrderFor("id"))
                    .isNotNull()
                    .satisfies(order -> assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC));
            verify(enrollmentRepository, never())
                    .findByClazz_Id(anyLong(), any(Pageable.class));
        }

        @Test
        @DisplayName("존재하지 않는 classId로 조회하면 CLASS_NOT_FOUND 예외가 발생하고 Repository 조회가 호출되지 않는다")
        void findClassEnrollments_classNotFound() {
            // given
            Long classId = 999L;
            given(classRepository.findById(classId)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> enrollmentService.findClassEnrollments(
                    CREATOR_ID, classId, null, DEFAULT_PAGEABLE))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CLASS_NOT_FOUND);

            verify(enrollmentRepository, never())
                    .findByClazz_Id(anyLong(), any(Pageable.class));
            verify(enrollmentRepository, never())
                    .findByClazz_IdAndStatus(anyLong(), any(EnrollmentStatus.class), any(Pageable.class));
        }

        @Test
        @DisplayName("다른 사용자가 강의 수강생 목록을 조회하면 CLASS_ACCESS_DENIED 예외가 발생하고 Repository 조회가 호출되지 않는다")
        void findClassEnrollments_notOwner_returnsAccessDenied() {
            // given
            Long classId = 1L;
            Long otherUserId = 201L;
            Class clazz = buildClass(classId, ClassStatus.OPEN, 30);
            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));

            // when & then
            assertThatThrownBy(() -> enrollmentService.findClassEnrollments(
                    otherUserId, classId, null, DEFAULT_PAGEABLE))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CLASS_ACCESS_DENIED);

            verify(enrollmentRepository, never())
                    .findByClazz_Id(anyLong(), any(Pageable.class));
            verify(enrollmentRepository, never())
                    .findByClazz_IdAndStatus(anyLong(), any(EnrollmentStatus.class), any(Pageable.class));
        }

        @Test
        @DisplayName("수강생이 없는 강의를 조회하면 content는 빈 리스트, totalElements는 0이 된다")
        void findClassEnrollments_emptyPage() {
            // given
            Long classId = 1L;
            Class clazz = buildClass(classId, ClassStatus.OPEN, 30);

            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));
            given(enrollmentRepository.findByClazz_Id(eq(classId), any(Pageable.class)))
                    .willReturn(new PageImpl<>(Collections.emptyList(), DEFAULT_PAGEABLE, 0));

            // when
            PageResponse<ClassEnrollmentItemResponse> response =
                    enrollmentService.findClassEnrollments(CREATOR_ID, classId, null, DEFAULT_PAGEABLE);

            // then
            assertThat(response.content()).isEmpty();
            assertThat(response.totalElements()).isZero();
            assertThat(response.totalPages()).isZero();
            assertThat(response.last()).isTrue();
        }
    }
}
