package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
import com.futureschole.courseregistration.dto.EnrollmentCreateRequest;
import com.futureschole.courseregistration.exception.CustomException;
import com.futureschole.courseregistration.exception.ErrorCode;
import com.futureschole.courseregistration.repository.ClassRepository;
import com.futureschole.courseregistration.repository.EnrollmentRepository;
import com.futureschole.courseregistration.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EnrollmentConcurrencyTest {

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private ClassRepository classRepository;

    @Autowired
    private EnrollmentRepository enrollmentRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        enrollmentRepository.deleteAllInBatch();
        classRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Nested
    @DisplayName("동시 수강 신청")
    class ConcurrentEnroll {

        @Test
        @DisplayName("capacity=10 강의에 30개의 동시 요청이 들어오면 10건만 성공하고 20건은 CLASS_FULL로 실패한다")
        void concurrent_enroll_only_capacity_succeeds() throws InterruptedException {
            // given
            int capacity = 10;
            int concurrency = 30;

            User creator = userRepository.save(buildUser("creator@example.com", UserRole.CREATOR));

            Class clazz = Class.create(
                    creator,
                    "동시성 테스트 강의",
                    "test",
                    50_000,
                    capacity,
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 7, 31)
            );
            setField(Class.class, clazz, "status", ClassStatus.OPEN);
            Class savedClass = classRepository.saveAndFlush(clazz);
            Long classId = savedClass.getId();

            List<Long> userIds = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                User student = userRepository.save(
                        buildUser("student" + i + "@example.com", UserRole.CLASSMATE));
                userIds.add(student.getId());
            }

            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrency);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger classFullCount = new AtomicInteger();
            AtomicInteger otherFailCount = new AtomicInteger();

            // when
            for (Long uid : userIds) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        enrollmentService.enroll(uid, new EnrollmentCreateRequest(classId));
                        successCount.incrementAndGet();
                    } catch (CustomException e) {
                        if (e.getErrorCode() == ErrorCode.CLASS_FULL) {
                            classFullCount.incrementAndGet();
                        } else {
                            otherFailCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        otherFailCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            boolean finishedInTime = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdownNow();

            // then
            assertThat(finishedInTime).isTrue();
            assertThat(successCount.get()).isEqualTo(capacity);
            assertThat(classFullCount.get()).isEqualTo(concurrency - capacity);
            assertThat(otherFailCount.get()).isZero();

            long activeEnrollmentCount = enrollmentRepository.countByClazz_IdAndStatusIn(
                    classId,
                    List.of(EnrollmentStatus.PENDING, EnrollmentStatus.CONFIRMED)
            );
            assertThat(activeEnrollmentCount).isEqualTo(capacity);
        }
    }

    private static User buildUser(String email, UserRole role) {
        try {
            Constructor<User> ctor = User.class.getDeclaredConstructor();
            ctor.setAccessible(true);
            User user = ctor.newInstance();
            setField(User.class, user, "email", email);
            setField(User.class, user, "password", "password");
            setField(User.class, user, "name", "name-" + email);
            setField(User.class, user, "role", role);
            return user;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void setField(java.lang.Class<?> type, Object target, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
