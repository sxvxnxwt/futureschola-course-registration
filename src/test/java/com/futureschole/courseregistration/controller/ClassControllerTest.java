package com.futureschole.courseregistration.controller;

import com.futureschole.courseregistration.exception.GlobalExceptionHandler;
import com.futureschole.courseregistration.service.ClassService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClassController.class)
@Import(GlobalExceptionHandler.class)
class ClassControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClassService classService;

    @Test
    @DisplayName("status=DRAFT 요청 시 400 VALIDATION_FAILED 응답을 반환한다")
    void getClasses_draftStatus_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/classes").param("status", "DRAFT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
