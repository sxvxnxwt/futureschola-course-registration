package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
import com.futureschole.courseregistration.dto.ClassCreateRequest;
import com.futureschole.courseregistration.dto.ClassCreateResponse;
import com.futureschole.courseregistration.exception.CustomException;
import com.futureschole.courseregistration.exception.ErrorCode;
import com.futureschole.courseregistration.repository.ClassRepository;
import com.futureschole.courseregistration.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ClassServiceTest {

    @Mock
    private ClassRepository classRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ClassService classService;

    private User creator;

    @BeforeEach
    void setUp() {
        creator = mock(User.class);
    }

    private ClassCreateRequest defaultRequest() {
        return new ClassCreateRequest(
                "웹 프로그래밍",
                "웹 프로그래밍 기초",
                50_000,
                30,
                LocalDate.of(2026, 6, 1),
                LocalDate.of(2026, 7, 31)
        );
    }

    @Test
    @DisplayName("CREATOR가 강의를 생성하면 DRAFT 상태로 저장된다")
    void createClass_success() {
        // given
        ClassCreateRequest request = defaultRequest();
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
        ClassCreateRequest request = defaultRequest();
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

        ClassCreateRequest request = defaultRequest();

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
