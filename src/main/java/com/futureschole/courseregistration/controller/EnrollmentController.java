package com.futureschole.courseregistration.controller;

import com.futureschole.courseregistration.dto.EnrollmentCreateRequest;
import com.futureschole.courseregistration.dto.EnrollmentCreateResponse;
import com.futureschole.courseregistration.service.EnrollmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
