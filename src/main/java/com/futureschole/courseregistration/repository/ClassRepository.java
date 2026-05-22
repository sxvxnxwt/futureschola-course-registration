package com.futureschole.courseregistration.repository;

import com.futureschole.courseregistration.domain.entity.Class;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClassRepository extends JpaRepository<Class, Long> {
}
