package com.futureschole.courseregistration.repository;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClassRepository extends JpaRepository<Class, Long> {

    List<Class> findAllByStatusInOrderByIdDesc(Collection<ClassStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT c FROM Class c WHERE c.id = :id")
    Optional<Class> findByIdForUpdate(@Param("id") Long id);
}
