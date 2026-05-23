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
class CancelConcurrencyTest {

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
    @DisplayName("결제 확정 vs 취소 경쟁")
    class ConfirmVsCancel {

        @Test
        @DisplayName("같은 enrollmentId에 confirm/cancel을 동시에 호출하면 정확히 한 쪽만 성공하고 최종 status는 CONFIRMED 또는 CANCELLED 중 하나가 된다")
        void concurrent_confirm_and_cancel_only_one_succeeds() throws InterruptedException {
            // given
            EnrollmentFixture fixture = seedPendingEnrollment("confirm-vs-cancel");
            Long enrollmentId = fixture.enrollmentId();
            Long studentId = fixture.studentId();

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(2);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger invalidTransitionCount = new AtomicInteger();
            AtomicInteger otherFailCount = new AtomicInteger();

            // when
            executor.submit(() -> runAndCount(
                    () -> enrollmentService.confirmPayment(studentId, enrollmentId),
                    startLatch, doneLatch, successCount, invalidTransitionCount, otherFailCount
            ));
            executor.submit(() -> runAndCount(
                    () -> enrollmentService.cancel(studentId, enrollmentId),
                    startLatch, doneLatch, successCount, invalidTransitionCount, otherFailCount
            ));

            startLatch.countDown();
            boolean finishedInTime = doneLatch.await(30, TimeUnit.SECONDS);
            executor.shutdownNow();

            // then
            assertThat(finishedInTime).isTrue();
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(invalidTransitionCount.get()).isEqualTo(1);
            assertThat(otherFailCount.get()).isZero();

            Enrollment finalEnrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
            assertThat(finalEnrollment.getStatus())
                    .isIn(EnrollmentStatus.CONFIRMED, EnrollmentStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("동시 취소")
    class ConcurrentCancel {

        @Test
        @DisplayName("같은 enrollmentId에 대한 동시 취소 10건 중 1건만 성공하고 나머지는 INVALID_STATUS_TRANSITION으로 실패한다")
        void concurrent_cancel_only_one_succeeds() throws InterruptedException {
            // given
            int concurrency = 10;
            EnrollmentFixture fixture = seedPendingEnrollment("cancel-duplicate");
            Long enrollmentId = fixture.enrollmentId();
            Long studentId = fixture.studentId();

            ExecutorService executor = Executors.newFixedThreadPool(concurrency);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(concurrency);
            AtomicInteger successCount = new AtomicInteger();
            AtomicInteger invalidTransitionCount = new AtomicInteger();
            AtomicInteger otherFailCount = new AtomicInteger();

            // when
            for (int i = 0; i < concurrency; i++) {
                executor.submit(() -> runAndCount(
                        () -> enrollmentService.cancel(studentId, enrollmentId),
                        startLatch, doneLatch, successCount, invalidTransitionCount, otherFailCount
                ));
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
            assertThat(finalEnrollment.getStatus()).isEqualTo(EnrollmentStatus.CANCELLED);
            assertThat(finalEnrollment.getCancelledAt()).isNotNull();
        }
    }

    private EnrollmentFixture seedPendingEnrollment(String slug) {
        User creator = userRepository.save(buildUser("creator-" + slug + "@example.com", UserRole.CREATOR));
        User student = userRepository.save(buildUser("student-" + slug + "@example.com", UserRole.CLASSMATE));

        Class clazz = Class.create(
                creator,
                "취소 동시성 테스트 - " + slug,
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
        return new EnrollmentFixture(savedEnrollment.getId(), student.getId());
    }

    private static void runAndCount(
            Runnable action,
            CountDownLatch startLatch,
            CountDownLatch doneLatch,
            AtomicInteger successCount,
            AtomicInteger invalidTransitionCount,
            AtomicInteger otherFailCount
    ) {
        try {
            startLatch.await();
            action.run();
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
    }

    private record EnrollmentFixture(Long enrollmentId, Long studentId) {
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
