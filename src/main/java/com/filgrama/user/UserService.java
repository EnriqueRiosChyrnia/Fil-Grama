package com.filgrama.user;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.filgrama.client.web.PageResponse;
import com.filgrama.domain.User;
import com.filgrama.domain.enums.Role;
import com.filgrama.error.ApiException;
import com.filgrama.repository.UserRepository;
import com.filgrama.user.dto.CreateUserRequest;
import com.filgrama.user.dto.UpdateUserRequest;
import com.filgrama.user.dto.UserResponse;

@Service
public class UserService {

    private final UserRepository users;
    private final UserQueryRepository userQuery;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;

    public UserService(UserRepository users,
                       UserQueryRepository userQuery,
                       PasswordEncoder passwordEncoder,
                       UserMapper mapper) {
        this.users = users;
        this.userQuery = userQuery;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(Role role, Boolean active, String q, Pageable pageable) {
        List<Specification<User>> specs = new ArrayList<>();
        if (role != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("role"), role));
        }
        if (active != null) {
            specs.add((root, query, cb) -> cb.equal(root.get("isActive"), active));
        }
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase() + "%";
            specs.add((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("fullName")), like),
                    cb.like(cb.lower(root.get("email")), like)));
        }
        Specification<User> spec = specs.stream().reduce(Specification::and).orElse(null);
        return PageResponse.of(userQuery.findAll(spec, pageable).map(mapper::toResponse));
    }

    @Transactional
    public UserResponse create(CreateUserRequest req) {
        if (users.existsByEmail(req.email())) {
            throw ApiException.conflict("Email already in use: " + req.email());
        }
        User u = new User();
        u.setEmail(req.email());
        u.setFullName(req.fullName());
        u.setRole(req.role());
        u.setActive(true);
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        return mapper.toResponse(users.save(u));
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return mapper.toResponse(find(id));
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req) {
        User u = find(id);
        if (req.fullName() != null) {
            if (req.fullName().isBlank()) {
                throw ApiException.badRequest("fullName must not be blank");
            }
            u.setFullName(req.fullName());
        }
        if (req.role() != null) {
            u.setRole(req.role());
        }
        if (req.isActive() != null) {
            u.setActive(req.isActive());
        }
        return mapper.toResponse(users.save(u));
    }

    private User find(Long id) {
        return users.findById(id)
                .orElseThrow(() -> ApiException.notFound("User %d not found".formatted(id)));
    }
}
