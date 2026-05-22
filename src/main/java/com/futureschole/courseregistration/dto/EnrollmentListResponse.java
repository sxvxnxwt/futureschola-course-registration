package com.futureschole.courseregistration.dto;

import java.util.List;

public record EnrollmentListResponse(
        List<EnrollmentListItemResponse> content
) {
}
