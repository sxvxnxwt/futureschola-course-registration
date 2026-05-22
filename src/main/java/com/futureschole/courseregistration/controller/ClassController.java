package com.futureschole.courseregistration.controller;

import com.futureschole.courseregistration.dto.ClassCreateRequest;
import com.futureschole.courseregistration.dto.ClassCreateResponse;
import com.futureschole.courseregistration.service.ClassService;
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
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    @PostMapping
    public ResponseEntity<ClassCreateResponse> createClass(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody ClassCreateRequest request
    ) {
        ClassCreateResponse response = classService.createClass(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
