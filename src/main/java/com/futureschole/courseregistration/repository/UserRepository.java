package com.futureschole.courseregistration.repository;

import com.futureschole.courseregistration.domain.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
