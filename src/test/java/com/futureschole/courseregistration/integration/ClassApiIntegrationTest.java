package com.futureschole.courseregistration.integration;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
import com.futureschole.courseregistration.dto.ClassCreateRequest;
import com.futureschole.courseregistration.dto.ClassStatusChangeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Class API 통합 테스트")
class ClassApiIntegrationTest extends IntegrationTestSupport {

    @Nested
    @DisplayName("강의 등록")
    class CreateClass {

        @Test
        @DisplayName("CREATOR가 강의를 등록하면 201 응답과 함께 DRAFT 상태로 영속화된다")
        void createClass_returns201_andPersistsAsDraft() throws Exception {
            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );

            ClassCreateRequest request = new ClassCreateRequest(
                    "Spring 통합 테스트 강의",
                    "통합 테스트 설명",
                    50_000,
                    20,
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 7, 31)
            );

            mockMvc.perform(post("/api/v1/classes")
                            .header("X-User-Id", creator.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.title").value("Spring 통합 테스트 강의"))
                    .andExpect(jsonPath("$.status").value("DRAFT"))
                    .andExpect(jsonPath("$.creatorId").value(creator.getId()));

            assertThat(classRepository.findAll())
                    .singleElement()
                    .satisfies(saved -> {
                        assertThat(saved.getStatus()).isEqualTo(ClassStatus.DRAFT);
                        assertThat(saved.getCreator().getId()).isEqualTo(creator.getId());
                    });
        }
    }

    @Nested
    @DisplayName("강의 상태 전이")
    class ChangeStatus {

        @Test
        @DisplayName("DRAFT → OPEN 전이는 200을 반환한다")
        void draftToOpen_succeeds() throws Exception {
            User creator = saveCreator();
            Class clazz = classRepository.save(TestFixtures.classOf(creator).build());

            mockMvc.perform(post("/api/v1/classes/{id}/status", clazz.getId())
                            .header("X-User-Id", creator.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new ClassStatusChangeRequest(ClassStatus.OPEN))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("OPEN"));
        }

        @Test
        @DisplayName("OPEN → CLOSED 전이는 200을 반환한다")
        void openToClosed_succeeds() throws Exception {
            User creator = saveCreator();
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.OPEN).build()
            );

            mockMvc.perform(post("/api/v1/classes/{id}/status", clazz.getId())
                            .header("X-User-Id", creator.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new ClassStatusChangeRequest(ClassStatus.CLOSED))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CLOSED"));
        }

        @Test
        @DisplayName("DRAFT → CLOSED 스킵 전이는 409 INVALID_STATUS_TRANSITION을 반환한다")
        void draftToClosed_skipFails() throws Exception {
            User creator = saveCreator();
            Class clazz = classRepository.save(TestFixtures.classOf(creator).build());

            mockMvc.perform(post("/api/v1/classes/{id}/status", clazz.getId())
                            .header("X-User-Id", creator.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new ClassStatusChangeRequest(ClassStatus.CLOSED))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        }

        @Test
        @DisplayName("OPEN → DRAFT 역방향 전이는 409 INVALID_STATUS_TRANSITION을 반환한다")
        void openToDraft_reverseFails() throws Exception {
            User creator = saveCreator();
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.OPEN).build()
            );

            mockMvc.perform(post("/api/v1/classes/{id}/status", clazz.getId())
                            .header("X-User-Id", creator.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new ClassStatusChangeRequest(ClassStatus.DRAFT))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        }

        @Test
        @DisplayName("CLOSED → OPEN 역방향 전이는 409 INVALID_STATUS_TRANSITION을 반환한다")
        void closedToOpen_reverseFails() throws Exception {
            User creator = saveCreator();
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.CLOSED).build()
            );

            mockMvc.perform(post("/api/v1/classes/{id}/status", clazz.getId())
                            .header("X-User-Id", creator.getId())
                            .contentType("application/json")
                            .content(objectMapper.writeValueAsString(new ClassStatusChangeRequest(ClassStatus.OPEN))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        }
    }

    @Nested
    @DisplayName("강의 목록 조회")
    class ListClasses {

        @Test
        @DisplayName("status=OPEN 필터 + 페이지네이션 응답 포맷을 반환한다")
        void listClasses_filterByOpen() throws Exception {
            User creator = saveCreator();
            classRepository.save(TestFixtures.classOf(creator).title("드래프트 강의").build());
            classRepository.save(TestFixtures.classOf(creator).title("오픈 강의 A").status(ClassStatus.OPEN).build());
            classRepository.save(TestFixtures.classOf(creator).title("오픈 강의 B").status(ClassStatus.OPEN).build());
            classRepository.save(TestFixtures.classOf(creator).title("종료 강의").status(ClassStatus.CLOSED).build());

            mockMvc.perform(get("/api/v1/classes").param("status", "OPEN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[*].status",
                            org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("OPEN"))))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.last").value(true));
        }
    }

    @Nested
    @DisplayName("강의 상세 조회")
    class ClassDetail {

        @Test
        @DisplayName("enrolledCount는 PENDING + CONFIRMED 합과 일치하고 CANCELLED는 제외된다")
        void enrolledCount_excludesCancelled() throws Exception {
            User creator = saveCreator();
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.OPEN).capacity(10).build()
            );

            User pendingStudent = userRepository.save(
                    TestFixtures.user().email("p@example.com").build()
            );
            User confirmedStudent = userRepository.save(
                    TestFixtures.user().email("c@example.com").build()
            );
            User cancelledStudent = userRepository.save(
                    TestFixtures.user().email("x@example.com").build()
            );

            enrollmentRepository.save(TestFixtures.enrollment(pendingStudent, clazz).build());
            enrollmentRepository.save(
                    TestFixtures.enrollment(confirmedStudent, clazz)
                            .status(EnrollmentStatus.CONFIRMED)
                            .confirmedAt(DEFAULT_NOW.atZone(TEST_ZONE).toLocalDateTime())
                            .build()
            );
            enrollmentRepository.save(
                    TestFixtures.enrollment(cancelledStudent, clazz)
                            .status(EnrollmentStatus.CANCELLED)
                            .cancelledAt(DEFAULT_NOW.atZone(TEST_ZONE).toLocalDateTime())
                            .build()
            );

            mockMvc.perform(get("/api/v1/classes/{id}", clazz.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(clazz.getId()))
                    .andExpect(jsonPath("$.status").value("OPEN"))
                    .andExpect(jsonPath("$.enrolledCount").value(2));
        }
    }

    @Nested
    @DisplayName("강의별 수강생 목록 조회 권한")
    class ClassEnrollmentsAuth {

        @Test
        @DisplayName("크리에이터 본인이 호출하면 200을 반환한다")
        void owner_canRead() throws Exception {
            User creator = saveCreator();
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.OPEN).build()
            );

            mockMvc.perform(get("/api/v1/classes/{id}/enrollments", clazz.getId())
                            .header("X-User-Id", creator.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }

        @Test
        @DisplayName("크리에이터가 아닌 사용자가 호출하면 403 CLASS_ACCESS_DENIED를 반환한다")
        void nonOwner_returns403() throws Exception {
            User creator = saveCreator();
            User other = userRepository.save(
                    TestFixtures.user().email("other@example.com").role(UserRole.CREATOR).build()
            );
            Class clazz = classRepository.save(
                    TestFixtures.classOf(creator).status(ClassStatus.OPEN).build()
            );

            mockMvc.perform(get("/api/v1/classes/{id}/enrollments", clazz.getId())
                            .header("X-User-Id", other.getId()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("CLASS_ACCESS_DENIED"));
        }
    }

    private User saveCreator() {
        return userRepository.save(
                TestFixtures.user().email("creator-" + System.nanoTime() + "@example.com")
                        .role(UserRole.CREATOR)
                        .build()
        );
    }
}
