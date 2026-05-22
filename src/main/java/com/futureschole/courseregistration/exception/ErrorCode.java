package com.futureschole.courseregistration.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "요청 값 검증에 실패했습니다."),

    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "해당 리소스에 대한 권한이 없습니다."),
    CLASS_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CLASS_ACCESS_DENIED", "해당 강의에 대한 권한이 없습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "사용자를 찾을 수 없습니다."),
    CLASS_NOT_FOUND(HttpStatus.NOT_FOUND, "CLASS_NOT_FOUND", "강의를 찾을 수 없습니다."),
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "ENROLLMENT_NOT_FOUND", "수강 신청 내역을 찾을 수 없습니다."),

    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", "허용되지 않은 상태 전이입니다."),
    CLASS_NOT_OPEN(HttpStatus.CONFLICT, "CLASS_NOT_OPEN", "신청할 수 없는 상태의 강의입니다."),
    CLASS_FULL(HttpStatus.CONFLICT, "CLASS_FULL", "정원이 가득 차 신청할 수 없습니다."),
    ALREADY_ENROLLED(HttpStatus.CONFLICT, "ALREADY_ENROLLED", "이미 신청한 강의입니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
