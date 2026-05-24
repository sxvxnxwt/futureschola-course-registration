package com.futureschole.courseregistration.integration;

import com.futureschole.courseregistration.domain.entity.Class;
import com.futureschole.courseregistration.domain.entity.Enrollment;
import com.futureschole.courseregistration.domain.entity.User;
import com.futureschole.courseregistration.domain.enums.ClassStatus;
import com.futureschole.courseregistration.domain.enums.EnrollmentStatus;
import com.futureschole.courseregistration.domain.enums.UserRole;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static UserBuilder user() {
        return new UserBuilder();
    }

    public static ClassBuilder classOf(User creator) {
        return new ClassBuilder(creator);
    }

    public static EnrollmentBuilder enrollment(User user, Class clazz) {
        return new EnrollmentBuilder(user, clazz);
    }

    public static final class UserBuilder {
        private String email = "user@example.com";
        private String password = "password";
        private String name = "tester";
        private UserRole role = UserRole.CLASSMATE;

        public UserBuilder email(String email) {
            this.email = email;
            return this;
        }

        public UserBuilder name(String name) {
            this.name = name;
            return this;
        }

        public UserBuilder role(UserRole role) {
            this.role = role;
            return this;
        }

        public User build() {
            User user = newInstance(User.class);
            setField(User.class, user, "email", email);
            setField(User.class, user, "password", password);
            setField(User.class, user, "name", name);
            setField(User.class, user, "role", role);
            return user;
        }
    }

    public static final class ClassBuilder {
        private final User creator;
        private String title = "테스트 강의";
        private String description = "테스트 설명";
        private Integer price = 50_000;
        private Integer capacity = 10;
        private LocalDate startDate = LocalDate.of(2026, 6, 1);
        private LocalDate endDate = LocalDate.of(2026, 7, 31);
        private ClassStatus status = ClassStatus.DRAFT;

        private ClassBuilder(User creator) {
            this.creator = creator;
        }

        public ClassBuilder title(String title) {
            this.title = title;
            return this;
        }

        public ClassBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ClassBuilder price(Integer price) {
            this.price = price;
            return this;
        }

        public ClassBuilder capacity(Integer capacity) {
            this.capacity = capacity;
            return this;
        }

        public ClassBuilder period(LocalDate startDate, LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
            return this;
        }

        public ClassBuilder status(ClassStatus status) {
            this.status = status;
            return this;
        }

        public Class build() {
            Class clazz = Class.create(creator, title, description, price, capacity, startDate, endDate);
            if (status != ClassStatus.DRAFT) {
                setField(Class.class, clazz, "status", status);
            }
            return clazz;
        }
    }

    public static final class EnrollmentBuilder {
        private final User user;
        private final Class clazz;
        private EnrollmentStatus status = EnrollmentStatus.PENDING;
        private LocalDateTime confirmedAt;
        private LocalDateTime cancelledAt;

        private EnrollmentBuilder(User user, Class clazz) {
            this.user = user;
            this.clazz = clazz;
        }

        public EnrollmentBuilder status(EnrollmentStatus status) {
            this.status = status;
            return this;
        }

        public EnrollmentBuilder confirmedAt(LocalDateTime confirmedAt) {
            this.confirmedAt = confirmedAt;
            return this;
        }

        public EnrollmentBuilder cancelledAt(LocalDateTime cancelledAt) {
            this.cancelledAt = cancelledAt;
            return this;
        }

        public Enrollment build() {
            Enrollment enrollment = Enrollment.create(user, clazz);
            if (status != EnrollmentStatus.PENDING) {
                setField(Enrollment.class, enrollment, "status", status);
            }
            if (confirmedAt != null) {
                setField(Enrollment.class, enrollment, "confirmedAt", confirmedAt);
            }
            if (cancelledAt != null) {
                setField(Enrollment.class, enrollment, "cancelledAt", cancelledAt);
            }
            return enrollment;
        }
    }

    private static <T> T newInstance(java.lang.Class<T> type) {
        try {
            Constructor<T> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to instantiate " + type.getName(), e);
        }
    }

    private static void setField(java.lang.Class<?> type, Object target, String name, Object value) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to set " + type.getName() + "#" + name, e);
        }
    }
}
