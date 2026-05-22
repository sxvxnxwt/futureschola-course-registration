package com.futureschole.courseregistration.repository;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ClassRepository extends JpaRepository<Class, Long> {

    List<Class> findAllByStatusInOrderByIdDesc(Collection<ClassStatus> statuses);
}
