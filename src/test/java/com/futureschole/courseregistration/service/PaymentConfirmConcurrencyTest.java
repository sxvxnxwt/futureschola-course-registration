package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PaymentConfirmConcurrencyTest {

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
    @DisplayName("동시 결제 확정")
    class ConcurrentConfirm {

        @Test
        @DisplayName("같은 enrollmentId에 대한 동시 결제 확정 10건 중 1건만 성공하고 나머지는 INVALID_STATUS_TRANSITION으로 실패한다")
        void concurrent_confirm_only_one_succeeds() throws InterruptedException {
            // given
            int concurrency = 10;

            User creator = userRepository.save(buildUser("creator@example.com", UserRole.CREATOR));
            User student = userRepository.save(buildUser("student@example.com", UserRole.CLASSMATE));

            Class clazz = Class.create(
                    creator,
                    "결제 확정 동시성 테스트",
                    "test",
                    50_000,
                    30,
                    LocalDate.of(2026, 6, 1),
                    LocalDate.of(2026, 7, 31)
            );
            setField(Class.class, clazz, "status", ClassStatus.OPEN);
            Class savedClass = classRepository.saveAndFlush(clazz);

            Enrollment enrollment = Enrollment.create(student, savedClass);
            Enrollment savedEnrollment = enrollmentRepository.saveAndFlush(enrollment);
            Long enrollmentId = savedEnrollment.getId();
            Long studentId = student.getId();

            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrency);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger invalidTransitionCount = new AtomicInteger();
            AtomicInteger otherFailCount = new AtomicInteger();

            // when
            for (int i = 0; i < concurrency; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        enrollmentService.confirmPayment(studentId, enrollmentId);
                        successCount.incrementAndGet();
                    } catch (CustomException e) {
                        if (e.getErrorCode() == ErrorCode.INVALID_STATUS_TRANSITION) {
                            invalidTransitionCount.incrementAndGet();
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
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(invalidTransitionCount.get()).isEqualTo(concurrency - 1);
            assertThat(otherFailCount.get()).isZero();

            Enrollment finalEnrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
            assertThat(finalEnrollment.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
            assertThat(finalEnrollment.getConfirmedAt()).isNotNull();
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
