package com.futureschole.courseregistration.service;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.dto.ClassCreateRequest;
import com.futureschole.courseregistration.dto.ClassCreateResponse;
import com.futureschole.courseregistration.exception.CustomException;
import com.futureschole.courseregistration.exception.ErrorCode;
import com.futureschole.courseregistration.repository.ClassRepository;
import com.futureschole.courseregistration.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassService {

    private final ClassRepository classRepository;
    private final UserRepository userRepository;

    @Transactional
    public ClassCreateResponse createClass(Long userId, ClassCreateRequest request) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Class clazz = Class.create(
                creator,
                request.title(),
                request.description(),
                request.price(),
                request.capacity(),
                request.startDate(),
                request.endDate()
        );

        Class saved = classRepository.save(clazz);
        return ClassCreateResponse.from(saved);
    }
}
