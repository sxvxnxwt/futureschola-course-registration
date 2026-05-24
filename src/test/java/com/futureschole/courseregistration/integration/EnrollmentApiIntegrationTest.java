package com.futureschole.courseregistration.integration;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
import com.futureschole.courseregistration.dto.EnrollmentCreateRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Enrollment API 통합 테스트")
class EnrollmentApiIntegrationTest extends IntegrationTestSupport {

    @Nested
    @DisplayName("신청 → 결제 → 취소 해피패스")
    class HappyPath {

        @Test
        @DisplayName("신청(PENDING) → 결제(CONFIRMED) → 취소(CANCELLED) 흐름이 모두 성공한다")
        void enroll_then_confirm_then_cancel() throws Exception {
            applyFixedClock(Instant.now());

            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );
            User student = userRepository.save(
                    TestFixtures.user().email("student@example.com").build()
            );
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.OPEN).capacity(10).build()
            );

            String enrollResponse = mockMvc.perform(post("/api/v1/enrollments")
                            .header("X-User-Id", student.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(clazz.getId()))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.status").value("PENDING"))
                    .andExpect(jsonPath("$.classId").value(clazz.getId()))
                    .andExpect(jsonPath("$.userId").value(student.getId()))
                    .andReturn().getResponse().getContentAsString();

            Long enrollmentId = objectMapper.readTree(enrollResponse).get("id").asLong();

            mockMvc.perform(post("/api/v1/enrollments/{id}/payment", enrollmentId)
                            .header("X-User-Id", student.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

            mockMvc.perform(post("/api/v1/enrollments/{id}/cancel", enrollmentId)
                            .header("X-User-Id", student.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
                    .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

            Enrollment finalEnrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
            assertThat(finalEnrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("신청 거부")
    class EnrollRejection {

        @Test
        @DisplayName("DRAFT 강의 신청은 404 CLASS_NOT_FOUND를 반환한다")
        void draftClass_returns404() throws Exception {
            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );
            User student = userRepository.save(TestFixtures.user().email("student@example.com").build());
            Class clazz = classRepository.save(TestFixtures.classOf(creator).build()); // DRAFT

            mockMvc.perform(post("/api/v1/enrollments")
                            .header("X-User-Id", student.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(clazz.getId()))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLASS_NOT_FOUND"));
        }

        @Test
        @DisplayName("CLOSED 강의 신청은 409 CLASS_NOT_OPEN을 반환한다")
        void closedClass_returns409() throws Exception {
            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );
            User student = userRepository.save(TestFixtures.user().email("student@example.com").build());
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.CLOSED).build()
            );

            mockMvc.perform(post("/api/v1/enrollments")
                            .header("X-User-Id", student.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(clazz.getId()))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CLASS_NOT_OPEN"));
        }

        @Test
        @DisplayName("정원이 가득 찬 강의 신청은 409 CLASS_FULL을 반환한다")
        void capacityReached_returns409() throws Exception {
            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.OPEN).capacity(1).build()
            );
            User firstStudent = userRepository.save(TestFixtures.user().email("first@example.com").build());
            enrollmentRepository.save(TestFixtures.enrollment(firstStudent, clazz).build());

            User lateStudent = userRepository.save(TestFixtures.user().email("late@example.com").build());

            mockMvc.perform(post("/api/v1/enrollments")
                            .header("X-User-Id", lateStudent.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(clazz.getId()))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CLASS_FULL"));
        }

        @Test
        @DisplayName("동일 사용자의 중복 신청은 409 ALREADY_ENROLLED를 반환한다")
        void duplicateEnroll_returns409() throws Exception {
            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );
            User student = userRepository.save(TestFixtures.user().email("student@example.com").build());
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.OPEN).capacity(10).build()
            );
            enrollmentRepository.save(TestFixtures.enrollment(student, clazz).build());

            mockMvc.perform(post("/api/v1/enrollments")
                            .header("X-User-Id", student.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new EnrollmentCreateRequest(clazz.getId()))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"));
        }
    }

    @Nested
    @DisplayName("결제 후 취소 가능 기간")
    class CancelDeadline {

        private static final LocalDateTime CONFIRMED_AT = LocalDateTime.of(2026, 5, 1, 12, 0);

        @Test
        @DisplayName("결제 후 7일 이내(경계 내)면 취소가 성공한다")
        void within7Days_cancelSucceeds() throws Exception {
            EnrollmentSeed seed = seedConfirmedEnrollment();
            applyFixedClock(LocalDateTime.of(2026, 5, 8, 11, 59, 59).toInstant(ZoneOffset.UTC));

            mockMvc.perform(post("/api/v1/enrollments/{id}/cancel", seed.enrollmentId)
                            .header("X-User-Id", seed.studentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELLED"));
        }

        @Test
        @DisplayName("결제 후 7일을 초과하면 409 CANCEL_PERIOD_EXPIRED를 반환한다")
        void after7Days_returns409() throws Exception {
            EnrollmentSeed seed = seedConfirmedEnrollment();
            applyFixedClock(LocalDateTime.of(2026, 5, 8, 12, 0, 1).toInstant(ZoneOffset.UTC));

            mockMvc.perform(post("/api/v1/enrollments/{id}/cancel", seed.enrollmentId)
                            .header("X-User-Id", seed.studentId))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANCEL_PERIOD_EXPIRED"));
        }

        private EnrollmentSeed seedConfirmedEnrollment() {
            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );
            User student = userRepository.save(TestFixtures.user().email("student@example.com").build());
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.OPEN).build()
            );
            Enrollment enrollment = enrollmentRepository.save(
                    TestFixtures.enrollment(student, clazz)
                            .status(EnrollmentStatus.CONFIRMED)
                            .confirmedAt(CONFIRMED_AT)
                            .build()
            );
            return new EnrollmentSeed(enrollment.getId(), student.getId());
        }

        private record EnrollmentSeed(Long enrollmentId, Long studentId) {
        }
    }

    @Nested
    @DisplayName("내 신청 목록 조회")
    class MyEnrollments {

        @Test
        @DisplayName("필터 없이 조회하면 본인의 모든 신청을 페이지로 반환한다")
        void listAll_paginated() throws Exception {
            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );
            User student = userRepository.save(TestFixtures.user().email("student@example.com").build());

            Class openClass = classRepository.save(
                    TestFixtures.classOf(creator).title("OPEN 강의").status(ClassStatus.OPEN).build()
            );
            Class anotherClass = classRepository.save(
                    TestFixtures.classOf(creator).title("다른 강의").status(ClassStatus.OPEN).build()
            );
            enrollmentRepository.save(TestFixtures.enrollment(student, openClass).build());
            enrollmentRepository.save(
                    TestFixtures.enrollment(student, anotherClass)
                            .status(EnrollmentStatus.CONFIRMED)
                            .confirmedAt(DEFAULT_NOW.atZone(TEST_ZONE).toLocalDateTime())
                            .build()
            );

            mockMvc.perform(get("/api/v1/enrollments/me")
                            .header("X-User-Id", student.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20));
        }

        @Test
        @DisplayName("status 필터로 CONFIRMED만 조회한다")
        void filterByConfirmed() throws Exception {
            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );
            User student = userRepository.save(TestFixtures.user().email("student@example.com").build());

            Class openClass = classRepository.save(
                    TestFixtures.classOf(creator).title("OPEN 강의").status(ClassStatus.OPEN).build()
            );
            Class anotherClass = classRepository.save(
                    TestFixtures.classOf(creator).title("다른 강의").status(ClassStatus.OPEN).build()
            );
            enrollmentRepository.save(TestFixtures.enrollment(student, openClass).build());
            enrollmentRepository.save(
                    TestFixtures.enrollment(student, anotherClass)
                            .status(EnrollmentStatus.CONFIRMED)
                            .confirmedAt(DEFAULT_NOW.atZone(TEST_ZONE).toLocalDateTime())
                            .build()
            );

            mockMvc.perform(get("/api/v1/enrollments/me")
                            .header("X-User-Id", student.getId())
                            .param("status", "CONFIRMED"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.totalElements").value(1));
        }
    }

    @Nested
    @DisplayName("X-User-Id 헤더 누락")
    class MissingHeader {

        @Test
        @DisplayName("X-User-Id 헤더 없이 호출하면 catch-all 핸들러가 500 INTERNAL_SERVER_ERROR를 반환한다")
        void missingHeader_returns500() throws Exception {
            mockMvc.perform(get("/api/v1/enrollments/me"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
        }
    }
}
