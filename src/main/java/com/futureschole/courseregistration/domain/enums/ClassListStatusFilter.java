package com.futureschole.courseregistration.domain.enums;

public enum ClassListStatusFilter {
    OPEN(ClassStatus.OPEN),
    CLOSED(ClassStatus.CLOSED);

    private final ClassStatus classStatus;

    ClassListStatusFilter(ClassStatus classStatus) {
        this.classStatus = classStatus;
    }

    public ClassStatus toClassStatus() {
        return classStatus;
    }
}
