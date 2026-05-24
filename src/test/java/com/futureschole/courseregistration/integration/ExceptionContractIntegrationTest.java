package com.futureschole.courseregistration.integration;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
import com.futureschole.courseregistration.dto.ClassCreateRequest;
import com.futureschole.courseregistration.dto.ClassStatusChangeRequest;
import com.futureschole.courseregistration.dto.EnrollmentCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GlobalExceptionHandler ErrorResponse 포맷 일관성 검증")
class ExceptionContractIntegrationTest extends IntegrationTestSupport {

    @Nested
    @DisplayName("Bean Validation 실패")
    class ValidationFailure {

        @Test
        @DisplayName("@NotBlank 위반 시 400 + code/message 포맷의 ErrorResponse를 반환한다")
        void invalidBody_returnsValidationFailed() throws Exception {
            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );

            ClassCreateRequest invalid = new ClassCreateRequest(
                    "",
                    "설명",
                    50_000,
                    10,
                    java.time.LocalDate.of(2026, 6, 1),
                    java.time.LocalDate.of(2026, 7, 31)
            );

            mockMvc.perform(post("/api/v1/classes")
                            .header("X-User-Id", creator.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(invalid)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("존재하지 않는 리소스")
    class ResourceNotFound {

        @Test
        @DisplayName("존재하지 않는 강의 ID로 신청 시 404 CLASS_NOT_FOUND를 반환한다")
        void unknownClass_returns404() throws Exception {
            User student = userRepository.save(
                    TestFixtures.user().email("student@example.com").build()
            );

            mockMvc.perform(post("/api/v1/enrollments")
                            .header("X-User-Id", student.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(999_999L))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("도메인 예외")
    class DomainException {

        @Test
        @DisplayName("허용되지 않는 상태 전이는 409 INVALID_STATUS_TRANSITION + code/message를 반환한다")
        void invalidStatusTransition_returns409() throws Exception {
            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.CLOSED).build()
            );

            mockMvc.perform(post("/api/v1/classes/{id}/status", clazz.getId())
                            .header("X-User-Id", creator.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new ClassStatusChangeRequest(ClassStatus.OPEN))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }

    @Nested
    @DisplayName("타입 변환 실패")
    class TypeMismatch {

        @Test
        @DisplayName("ClassListStatusFilter에 없는 enum 값은 400 VALIDATION_FAILED를 반환한다")
        void unknownEnumParam_returnsValidationFailed() throws Exception {
            mockMvc.perform(get("/api/v1/classes").param("status", "DRAFT"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.message").isNotEmpty());
        }
    }
}
