package com.futureschole.courseregistration.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.futureschole.courseregistration.repository.ClassRepository;
import com.futureschole.courseregistration.repository.EnrollmentRepository;
import com.futureschole.courseregistration.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class IntegrationTestSupport {

    @SuppressWarnings("resource")
    private static final MySQLContainer<?> MYSQL_CONTAINER =
            new MySQLContainer<>("mysql:8.0.36")
                    .withDatabaseName("course_registration")
                    .withUsername("test")
                    .withPassword("test")
                    .withReuse(true);

    static {
        MYSQL_CONTAINER.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL_CONTAINER::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL_CONTAINER::getUsername);
        registry.add("spring.datasource.password", MYSQL_CONTAINER::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    protected MockMvc mockMvc;

    protected final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ClassRepository classRepository;

    @Autowired
    protected EnrollmentRepository enrollmentRepository;

    @MockitoBean
    protected Clock clock;

    protected static final ZoneId TEST_ZONE = ZoneOffset.UTC;
    protected static final Instant DEFAULT_NOW = Instant.parse("2026-05-24T00:00:00Z");

    @BeforeEach
    void initClock() {
        applyFixedClock(DEFAULT_NOW);
    }

    protected void applyFixedClock(Instant instant) {
        Clock fixed = Clock.fixed(instant, TEST_ZONE);
        Mockito.when(clock.instant()).thenReturn(fixed.instant());
        Mockito.when(clock.getZone()).thenReturn(fixed.getZone());
    }

    @AfterEach
    void cleanUp() {
        enrollmentRepository.deleteAllInBatch();
        classRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }
}
