package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassListStatusFilter;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
import com.futureschole.courseregistration.dto.*;
import com.futureschole.courseregistration.exception.CustomException;
import com.futureschole.courseregistration.exception.ErrorCode;
import com.futureschole.courseregistration.repository.ClassRepository;
import com.futureschole.courseregistration.repository.EnrollmentRepository;
import com.futureschole.courseregistration.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClassServiceTest {

    @Mock
    private ClassRepository classRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @InjectMocks
    private ClassService classService;

    private static ClassCreateRequest defaultCreateRequest() {
        return new ClassCreateRequest(
                "웹 프로그래밍",
                "웹 프로그래밍 기초",
                50_000,
                30,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 31)
        );
    }

    private static Class buildClassFixture(Long ownerId, ClassStatus status) {
        User owner = mock(User.class);
        lenient().when(owner.getId()).thenReturn(ownerId);
        given(owner.getRole()).willReturn(UserRole.CREATOR);

        Class clazz = Class.create(
                owner,
                "웹 프로그래밍",
                "웹 프로그래밍 기초",
                50_000,
                30,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 31)
        );
        setStatus(clazz, status);
        return clazz;
    }

    private static void setStatus(Class clazz, ClassStatus status) {
        try {
            Field field = Class.class.getDeclaredField("status");
            field.setAccessible(true);
            field.set(clazz, status);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setId(Class clazz, Long id) {
        try {
            Field field = Class.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(clazz, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("강의 생성")
    class CreateClass {

        private User creator;

        @BeforeEach
        void setUp() {
            creator = mock(User.class);
        }

        @Test
        @DisplayName("CREATOR가 강의를 생성하면 DRAFT 상태로 저장된다")
        void createClass_success() {
            // given
            ClassCreateRequest request = defaultCreateRequest();
            given(creator.getId()).willReturn(1L);
            given(creator.getRole()).willReturn(UserRole.CREATOR);
            given(userRepository.findById(1L)).willReturn(Optional.of(creator));
            given(classRepository.save(any(Class.class))).willAnswer(invocation -> invocation.getArgument(0));

            // when
            ClassCreateResponse response = classService.createClass(1L, request);

            // then
            assertThat(response.title()).isEqualTo("웹 프로그래밍");
            assertThat(response.status()).isEqualTo(ClassStatus.DRAFT);
            assertThat(response.creatorId()).isEqualTo(1L);
            verify(classRepository).save(any(Class.class));
        }

        @Test
        @DisplayName("사용자가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다")
        void createClass_userNotFound() {
            // given
            ClassCreateRequest request = defaultCreateRequest();
            given(userRepository.findById(99L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> classService.createClass(99L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.USER_NOT_FOUND);

            verify(classRepository, never()).save(any(Class.class));
        }

        @Test
        @DisplayName("사용자 권한이 CREATOR가 아니면 VALIDATION_FAILED 예외가 발생한다")
        void createClass_notCreatorRole() {
            // given
            User classmate = mock(User.class);
            given(classmate.getRole()).willReturn(UserRole.CLASSMATE);
            given(userRepository.findById(1L)).willReturn(Optional.of(classmate));

            ClassCreateRequest request = defaultCreateRequest();

            // when & then
            assertThatThrownBy(() -> classService.createClass(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);

            verify(classRepository, never()).save(any(Class.class));
        }

        @Test
        @DisplayName("정원이 0 이하면 VALIDATION_FAILED 예외가 발생한다")
        void createClass_invalidCapacity() {
            // given
            given(creator.getRole()).willReturn(UserRole.CREATOR);
            given(userRepository.findById(1L)).willReturn(Optional.of(creator));

            ClassCreateRequest request = new ClassCreateRequest(
                    "웹 프로그래밍",
                    "웹 프로그래밍 기초",
                    50_000,
                    0,
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 7, 31)
            );

            // when & then
            assertThatThrownBy(() -> classService.createClass(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);

            verify(classRepository, never()).save(any(Class.class));
        }

        @Test
        @DisplayName("종료일이 시작일보다 빠르면 VALIDATION_FAILED 예외가 발생한다")
        void createClass_endDateBeforeStartDate() {
            // given
            given(creator.getRole()).willReturn(UserRole.CREATOR);
            given(userRepository.findById(1L)).willReturn(Optional.of(creator));

            ClassCreateRequest request = new ClassCreateRequest(
                    "웹 프로그래밍 입문",
                    "웹 프로그래밍 기초",
                    50_000,
                    30,
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 5, 31)
            );

            // when & then
            assertThatThrownBy(() -> classService.createClass(1L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);

            verify(classRepository, never()).save(any(Class.class));
        }
    }

    @Nested
    @DisplayName("강의 상태 변경")
    class ChangeStatus {

        @Test
        @DisplayName("DRAFT 상태의 본인 강의를 OPEN으로 변경할 수 있다")
        void changeStatus_draftToOpen_success() {
            // given
            Long userId = 1L;
            Long classId = 10L;
            Class clazz = buildClassFixture(userId, ClassStatus.DRAFT);
            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));

            ClassStatusChangeRequest request = new ClassStatusChangeRequest(ClassStatus.OPEN);

            // when
            ClassStatusChangeResponse response = classService.changeStatus(userId, classId, request);

            // then
            assertThat(response.status()).isEqualTo(ClassStatus.OPEN);
            assertThat(clazz.getStatus()).isEqualTo(ClassStatus.OPEN);
        }

        @Test
        @DisplayName("강의가 존재하지 않으면 CLASS_NOT_FOUND 예외가 발생한다")
        void changeStatus_classNotFound() {
            // given
            given(classRepository.findById(99L)).willReturn(Optional.empty());
            ClassStatusChangeRequest request = new ClassStatusChangeRequest(ClassStatus.OPEN);

            // when & then
            assertThatThrownBy(() -> classService.changeStatus(1L, 99L, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CLASS_NOT_FOUND);
        }

        @Test
        @DisplayName("본인 소유가 아닌 강의의 상태를 바꾸려 하면 CLASS_ACCESS_DENIED 예외가 발생한다")
        void changeStatus_notOwner() {
            // given
            Long ownerId = 1L;
            Long otherUserId = 2L;
            Long classId = 10L;
            Class clazz = buildClassFixture(ownerId, ClassStatus.DRAFT);
            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));

            ClassStatusChangeRequest request = new ClassStatusChangeRequest(ClassStatus.OPEN);

            // when & then
            assertThatThrownBy(() -> classService.changeStatus(otherUserId, classId, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CLASS_ACCESS_DENIED);

            assertThat(clazz.getStatus()).isEqualTo(ClassStatus.DRAFT);
        }

        @Test
        @DisplayName("CLOSED 상태에서 OPEN으로 전이하면 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void changeStatus_closedToOpen_invalid() {
            // given
            Long userId = 1L;
            Long classId = 10L;
            Class clazz = buildClassFixture(userId, ClassStatus.CLOSED);
            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));

            ClassStatusChangeRequest request = new ClassStatusChangeRequest(ClassStatus.OPEN);

            // when & then
            assertThatThrownBy(() -> classService.changeStatus(userId, classId, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);

            assertThat(clazz.getStatus()).isEqualTo(ClassStatus.CLOSED);
        }

        @Test
        @DisplayName("DRAFT 상태에서 CLOSED로 바로 전이하면 INVALID_STATUS_TRANSITION 예외가 발생한다")
        void changeStatus_draftToClosed_invalid() {
            // given
            Long userId = 1L;
            Long classId = 10L;
            Class clazz = buildClassFixture(userId, ClassStatus.DRAFT);
            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));

            ClassStatusChangeRequest request = new ClassStatusChangeRequest(ClassStatus.CLOSED);

            // when & then
            assertThatThrownBy(() -> classService.changeStatus(userId, classId, request))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);

            assertThat(clazz.getStatus()).isEqualTo(ClassStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("강의 목록 조회")
    class GetClasses {

        private static final Pageable DEFAULT_PAGEABLE =
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "id"));

        @Test
        @DisplayName("status=OPEN 필터링 시 OPEN 강의만 조회된다")
        void getClasses_filterOpen() {
            // given
            Class openClass = buildClassFixture(1L, ClassStatus.OPEN);
            given(classRepository.findAllByStatusIn(eq(List.of(ClassStatus.OPEN)), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(openClass), DEFAULT_PAGEABLE, 1));

            // when
            PageResponse<ClassListItemResponse> response =
                    classService.getClasses(ClassListStatusFilter.OPEN, DEFAULT_PAGEABLE);

            // then
            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).status()).isEqualTo(ClassStatus.OPEN);
        }

        @Test
        @DisplayName("status=CLOSED 필터링 시 CLOSED 강의만 조회된다")
        void getClasses_filterClosed() {
            // given
            Class closedClass = buildClassFixture(1L, ClassStatus.CLOSED);
            given(classRepository.findAllByStatusIn(eq(List.of(ClassStatus.CLOSED)), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(closedClass), DEFAULT_PAGEABLE, 1));

            // when
            PageResponse<ClassListItemResponse> response =
                    classService.getClasses(ClassListStatusFilter.CLOSED, DEFAULT_PAGEABLE);

            // then
            assertThat(response.content()).hasSize(1);
            assertThat(response.content().get(0).status()).isEqualTo(ClassStatus.CLOSED);
        }

        @Test
        @DisplayName("status 미지정 시 OPEN+CLOSED 강의가 조회되고 DRAFT는 제외된다")
        void getClasses_defaultFilter() {
            // given
            Class openClass = buildClassFixture(1L, ClassStatus.OPEN);
            Class closedClass = buildClassFixture(2L, ClassStatus.CLOSED);
            given(classRepository.findAllByStatusIn(anyCollection(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(List.of(openClass, closedClass), DEFAULT_PAGEABLE, 2));

            // when
            PageResponse<ClassListItemResponse> response =
                    classService.getClasses(null, DEFAULT_PAGEABLE);

            // then
            ArgumentCaptor<Collection<ClassStatus>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(classRepository).findAllByStatusIn(captor.capture(), any(Pageable.class));
            assertThat(captor.getValue())
                    .containsExactlyInAnyOrder(ClassStatus.OPEN, ClassStatus.CLOSED)
                    .doesNotContain(ClassStatus.DRAFT);

            assertThat(response.content()).hasSize(2);
            assertThat(response.content())
                    .extracting("status")
                    .containsExactlyInAnyOrder(ClassStatus.OPEN, ClassStatus.CLOSED);
        }

        @Test
        @DisplayName("결과가 없으면 content는 빈 리스트, totalElements는 0이 된다")
        void getClasses_emptyResult() {
            // given
            given(classRepository.findAllByStatusIn(anyCollection(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(Collections.emptyList(), DEFAULT_PAGEABLE, 0));

            // when
            PageResponse<ClassListItemResponse> response =
                    classService.getClasses(ClassListStatusFilter.OPEN, DEFAULT_PAGEABLE);

            // then
            assertThat(response.content()).isEmpty();
            assertThat(response.totalElements()).isZero();
            assertThat(response.totalPages()).isZero();
            assertThat(response.last()).isTrue();
        }

        @Test
        @DisplayName("호출 시 전달된 Pageable이 Repository에 그대로 전파된다")
        void getClasses_passesPageableToRepository() {
            // given
            Pageable pageable = PageRequest.of(2, 10, Sort.by(Sort.Direction.ASC, "id"));
            given(classRepository.findAllByStatusIn(anyCollection(), any(Pageable.class)))
                    .willReturn(new PageImpl<>(Collections.emptyList(), pageable, 0));

            // when
            classService.getClasses(null, pageable);

            // then
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(classRepository).findAllByStatusIn(anyCollection(), captor.capture());
            Pageable captured = captor.getValue();
            assertThat(captured.getPageNumber()).isEqualTo(2);
            assertThat(captured.getPageSize()).isEqualTo(10);
            assertThat(captured.getSort().getOrderFor("id"))
                    .isNotNull()
                    .satisfies(order -> assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC));
        }

        @Test
        @DisplayName("Repository가 반환한 Page는 PageResponse의 모든 필드로 정확히 매핑된다")
        void getClasses_mapsPageMetadataIntoPageResponse() {
            // given
            Class c1 = buildClassFixture(1L, ClassStatus.OPEN);
            Class c2 = buildClassFixture(2L, ClassStatus.OPEN);
            Pageable pageable = PageRequest.of(1, 2, Sort.by(Sort.Direction.DESC, "id"));
            Page<Class> page = new PageImpl<>(List.of(c1, c2), pageable, 5);
            given(classRepository.findAllByStatusIn(anyCollection(), any(Pageable.class)))
                    .willReturn(page);

            // when
            PageResponse<ClassListItemResponse> response =
                    classService.getClasses(null, pageable);

            // then
            assertThat(response.content()).hasSize(2);
            assertThat(response.page()).isEqualTo(1);
            assertThat(response.size()).isEqualTo(2);
            assertThat(response.totalElements()).isEqualTo(5);
            assertThat(response.totalPages()).isEqualTo(3);
            assertThat(response.last()).isFalse();
        }
    }

    @Nested
    @DisplayName("강의 상세 조회")
    class GetClassDetail {

        @Test
        @DisplayName("OPEN 상태 강의를 조회하면 PENDING+CONFIRMED 합계가 enrolledCount로 반환된다")
        void getClassDetail_success_calculatesEnrolledCount() {
            // given
            Long classId = 10L;
            Class clazz = buildClassFixture(1L, ClassStatus.OPEN);
            setId(clazz, classId);
            given(classRepository.findById(classId)).willReturn(Optional.of(clazz));
            given(enrollmentRepository.countByClazz_IdAndStatusIn(
                    classId,
                    List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED))
            ).willReturn(12L);

            // when
            ClassDetailResponse response = classService.getClassDetail(classId);

            // then
            assertThat(response.id()).isEqualTo(classId);
            assertThat(response.status()).isEqualTo(ClassStatus.OPEN);
            assertThat(response.enrolledCount()).isEqualTo(12L);
            assertThat(response.capacity()).isEqualTo(30);
            assertThat(response.creatorId()).isEqualTo(1L);
            verify(enrollmentRepository).countByClazz_IdAndStatusIn(
                    classId,
                    List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)
            );
        }

        @Test
        @DisplayName("존재하지 않는 id로 조회하면 CLASS_NOT_FOUND 예외가 발생한다")
        void getClassDetail_notFound() {
            // given
            given(classRepository.findById(99L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> classService.getClassDetail(99L))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CLASS_NOT_FOUND);

            verify(enrollmentRepository, never())
                    .countByClazz_IdAndStatusIn(any(), anyCollection());
        }

        @Test
        @DisplayName("DRAFT 상태 강의를 조회하면 CLASS_NOT_FOUND 예외가 발생한다")
        void getClassDetail_draftHidden() {
            // given
            Long classId = 10L;
            Class draftClass = buildClassFixture(1L, ClassStatus.DRAFT);
            setId(draftClass, classId);
            given(classRepository.findById(classId)).willReturn(Optional.of(draftClass));

            // when & then
            assertThatThrownBy(() -> classService.getClassDetail(classId))
                    .isInstanceOf(CustomException.class)
                    .extracting("errorCode")
                    .isEqualTo(ErrorCode.CLASS_NOT_FOUND);

            verify(enrollmentRepository, never())
                    .countByClazz_IdAndStatusIn(any(), anyCollection());
        }
    }
}
