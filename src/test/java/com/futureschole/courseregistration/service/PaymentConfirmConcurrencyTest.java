package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
import com.futureschole.courseregistration.exception.CustomException;
import com.futureschole.courseregistration.exception.ErrorCode;
import com.futureschole.courseregistration.integration.IntegrationTestSupport;
import com.futureschole.courseregistration.integration.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentConfirmConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private EnrollmentService enrollmentService;

    @Nested
    @DisplayName("동시 결제 확정")
    class ConcurrentConfirm {

        @Test
        @DisplayName("같은 enrollmentId에 대한 동시 결제 확정 10건 중 1건만 성공하고 나머지는 INVALID_STATUS_TRANSITION으로 실패한다")
        void concurrent_confirm_only_one_succeeds() throws InterruptedException {
            // given
            int concurrency = 10;

            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );
            User student = userRepository.save(
                    TestFixtures.user().email("student@example.com").build()
            );
            Class savedClass = classRepository.saveAndFlush(
                    TestFixtures.classOf(creator)
                            .title("결제 확정 동시성 테스트")
                            .capacity(30)
                            .status(ClassStatus.OPEN)
                            .build()
            );

            Enrollment savedEnrollment = enrollmentRepository.saveAndFlush(
                    TestFixtures.enrollment(student, savedClass).build()
            );
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
}
