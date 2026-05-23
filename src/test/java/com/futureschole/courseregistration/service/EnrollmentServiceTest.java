package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            Enrollment enrollment = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.PENDING);

            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when
            EnrollmentCancelResponse response = enrollmentService.cancel(userId, enrollmentId);

            // then
            assertThat(response.id()).isEqualTo(enrollmentId);
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(response.cancelledAt()).isNotNull();

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(enrollment.getCancelledAt()).isNotNull();
            assertThat(enrollment.getConfirmedAt()).isNull();
        }

        @Test
        @DisplayName("CONFIRMED 상태의 본인 enrollment를 취소하면 CANCELLED로 전이되며 confirmedAt은 보존된다")
        void cancel_fromConfirmed_success() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            Enrollment enrollment = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CONFIRMED);
            LocalDateTime confirmedAt = LocalDateTime.of(2026, 5, 1, 10, 0);
            setEnrollmentField(enrollment, "confirmedAt", confirmedAt);

            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when
            EnrollmentCancelResponse response = enrollmentService.cancel(userId, enrollmentId);

            // then
            assertThat(response.id()).isEqualTo(enrollmentId);
            assertThat(response.status()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(response.cancelledAt()).isNotNull();

            assertThat(enrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(enrollment.getCancelledAt()).isNotNull();
            assertThat(enrollment.getConfirmedAt()).isEqualTo(confirmedAt);
        }

        @Test
        @DisplayName("이미 CANCELLED 상태인 enrollment를 다시 취소하면 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void cancel_alreadyCancelled_returnsInvalidStatusTransition() {
            // given
            Long userId = 200L;
            Long enrollmentId = 500L;
            Class clazz = buildClass(1L, ClassStatus.OPEN, 30);
            User user = buildUserRef(userId);
            Enrollment enrollment = buildEnrollment(enrollmentId, user, clazz, EnrollmentStatus.CANCELLED);

            given(enrollmentRepository.findById(enrollmentId)).willReturn(Optional.of(enrollment));

            // when & then
            assertThatThrownBy(() -> enrollmentService.cancel(userId, enrollmentId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
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
        }
    }

    @Nested
    @DisplayName("내 수강 신청 목록 조회")
    class FindMyEnrollments {

        @Test
        @DisplayName("status 필터가 없으면 본인의 모든 상태 신청 내역을 createdAt DESC로 반환한다")
        void findMyEnrollments_withoutStatusFilter_returnsAll() {
            // given
            Long userId = 200L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            List<EnrollmentListItemResponse> repoResult = List.of(
                    new EnrollmentListItemResponse(
                            502L, 2L, "데이터베이스 기초",
                            EnrollmentStatus.CANCELLED, now.minusHours(1), null
                    ),
                    new EnrollmentListItemResponse(
                            501L, 1L, "웹 프로그래밍 입문",
                            EnrollmentStatus.CONFIRMED, now.minusHours(2), now.minusMinutes(30)
                    ),
                    new EnrollmentListItemResponse(
                            500L, 3L, "알고리즘 입문",
                            EnrollmentStatus.PENDING, now.minusHours(3), null
                    )
            );
            given(enrollmentRepository.findMyEnrollments(userId)).willReturn(repoResult);

            // when
            EnrollmentListResponse response = enrollmentService.findMyEnrollments(userId, null);

            // then
            assertThat(response.content()).hasSize(3);
            assertThat(response.content())
                    .extracting(EnrollmentListItemResponse::status)
                    .containsExactly(
                            EnrollmentStatus.CANCELLED,
                            EnrollmentStatus.CONFIRMED,
                            EnrollmentStatus.PENDING
                    );
            verify(enrollmentRepository, never())
                    .findMyEnrollmentsByStatus(anyLong(), any(EnrollmentStatus.class));
        }

        @Test
        @DisplayName("status=CONFIRMED 필터 시 PENDING 신청은 제외되고 CONFIRMED 건만 반환된다")
        void findMyEnrollments_withConfirmedFilter_excludesPending() {
            // given
            Long userId = 200L;
            LocalDateTime now = LocalDateTime.of(2026, 5, 22, 10, 0);
            List<EnrollmentListItemResponse> repoResult = List.of(
                    new EnrollmentListItemResponse(
                            501L, 1L, "웹 프로그래밍 입문",
                            EnrollmentStatus.CONFIRMED, now.minusHours(2), now.minusMinutes(30)
                    )
            );
            given(enrollmentRepository.findMyEnrollmentsByStatus(userId, EnrollmentStatus.CONFIRMED))
                    .willReturn(repoResult);

            // when
            EnrollmentListResponse response = enrollmentService.findMyEnrollments(userId, EnrollmentStatus.CONFIRMED);

            // then
            assertThat(response.content()).hasSize(1);
            assertThat(response.content())
                    .extracting(EnrollmentListItemResponse::status)
                    .containsExactly(EnrollmentStatus.CONFIRMED);
            assertThat(response.content())
                    .extracting(EnrollmentListItemResponse::status)
                    .doesNotContain(EnrollmentStatus.PENDING);
            verify(enrollmentRepository, never()).findMyEnrollments(anyLong());
        }
    }
}
