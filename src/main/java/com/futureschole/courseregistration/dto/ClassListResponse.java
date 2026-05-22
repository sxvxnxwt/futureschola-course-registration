package com.futureschole.courseregistration.dto;

import java.util.List;

public record ClassListResponse(
        List<ClassListItemResponse> content
) {
}
