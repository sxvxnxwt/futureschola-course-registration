package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;
import com.futureschole.courseregistration.dto.EnrollmentCreateRequest;
import com.futureschole.courseregistration.exception.CustomException;
import com.futureschole.courseregistration.exception.ErrorCode;
import com.futureschole.courseregistration.integration.IntegrationTestSupport;
import com.futureschole.courseregistration.integration.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class EnrollmentConcurrencyTest extends IntegrationTestSupport {

    @Autowired
    private EnrollmentService enrollmentService;

    @Nested
    @DisplayName("동시 수강 신청")
    class ConcurrentEnroll {

        @Test
        @DisplayName("capacity=10 강의에 30개의 동시 요청이 들어오면 10건만 성공하고 20건은 CLASS_FULL로 실패한다")
        void concurrent_enroll_only_capacity_succeeds() throws InterruptedException {
            // given
            int capacity = 10;
            int concurrency = 30;

            User creator = userRepository.save(
                    TestFixtures.user().email("creator@example.com").role(UserRole.CREATOR).build()
            );
            Class savedClass = classRepository.saveAndFlush(
                    TestFixtures.classOf(creator)
                            .title("동시성 테스트 강의")
                            .capacity(capacity)
                            .status(ClassStatus.OPEN)
                            .build()
            );
            Long classId = savedClass.getId();

            List<Long> userIds = new ArrayList<>();
            for (int i = 0; i < concurrency; i++) {
                User student = userRepository.save(
                        TestFixtures.user().email("student" + i + "@example.com").build()
                );
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
}
