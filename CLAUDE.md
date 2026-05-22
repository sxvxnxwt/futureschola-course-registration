## 기술 스택
- Java 21, Spring Boot 4.0, Spring Data JPA, MySQL
- 빌드: Gradle
- 테스트: JUnit 5, AssertJ, Mockito(필요 시)

## 패키지 구조
```texxt
com.futureschole.courseregistration
├── controller/
├── service/
├── repository/
├── domain/
├── dto/
├── exception/
└── config/
```

## API 컨벤션
- Base URL: `/api/v1`
- 인증: `X-User-Id` 헤더로 사용자 식별 (Spring Security 미사용)
- Content-Type: `application/json`
- 시간 형식: ISO-8601 (UTC)
- 페이지네이션: Spring Data `Pageable` + `Page<T>` 응답

## 에러 응답
- GlobalExceptionHandler에서 통일 처리
- 응답 포맷:
  ```json
  { "code": "CLASS_FULL", "message": "...", "timestamp": "..." }
- 비즈니스 위반은 409 Conflict + 도메인 에러 코드
- 검증 실패는 400 VALIDATION_FAILED

## 코드 스타일
- Lombok 사용
- DTO는 Java record 우선
- 컨트롤러는 얇게, 비즈니스 로직은 Service에
- 도메인 규칙은 엔티티 메서드로 (예: Class.canAccept(), Enrollment.confirm())
