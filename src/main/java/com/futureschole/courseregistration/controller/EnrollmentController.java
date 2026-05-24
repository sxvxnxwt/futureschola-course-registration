package com.futureschole.courseregistration.controller;

import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.dto.EnrollmentCancelResponse;
import com.futureschole.courseregistration.dto.EnrollmentCreateRequest;
import com.futureschole.courseregistration.dto.EnrollmentCreateResponse;
import com.futureschole.courseregistration.dto.EnrollmentListItemResponse;
import com.futureschole.courseregistration.dto.PageResponse;
import com.futureschole.courseregistration.dto.PaymentConfirmResponse;
import com.futureschole.courseregistration.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @PostMapping
    public ResponseEntity<EnrollmentCreateResponse> enroll(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody EnrollmentCreateRequest request
    ) {
        EnrollmentCreateResponse response = enrollmentService.enroll(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{enrollmentId}/payment")
    public ResponseEntity<PaymentConfirmResponse> confirmPayment(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long enrollmentId
    ) {
        PaymentConfirmResponse response = enrollmentService.confirmPayment(userId, enrollmentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<PageResponse<EnrollmentListItemResponse>> findMyEnrollments(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) EnrollmentStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(enrollmentService.findMyEnrollments(userId, status, pageable));
    }

    @PostMapping("/{enrollmentId}/cancel")
    public ResponseEntity<EnrollmentCancelResponse> cancel(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long enrollmentId
    ) {
        EnrollmentCancelResponse response = enrollmentService.cancel(userId, enrollmentId);
        return ResponseEntity.ok(response);
    }
}
