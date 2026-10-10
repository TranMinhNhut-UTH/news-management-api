package com.uth.news.service;

import com.uth.news.dto.request.*;
import com.uth.news.dto.response.*;
import com.uth.news.entity.User;
import com.uth.news.exception.*;
import com.uth.news.repository.UserRepository;
import com.uth.news.security.JwtService;
import java.nio.charset.StandardCharsets;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final String dummyPasswordHash;

    public AuthService(UserRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
    }

    public UserResponse register(RegisterRequest request) {
        validatePasswordBytes(request.password());
        if (repository.existsByUsername(request.username())) {
            throw new ConflictException("Username already exists");
        }
        User user = new User(request.username(), passwordEncoder.encode(request.password()));
        try {
            return UserResponse.from(repository.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            // The database unique constraint also protects simultaneous registrations.
            throw new ConflictException("Username already exists");
        }
    }

    public LoginResponse login(LoginRequest request) {
        validatePasswordBytes(request.password());
        var user = repository.findByUsername(request.username());
        String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user.isEmpty() || !matches) {
            throw new UnauthorizedException("Invalid username or password");
        }
        return new LoginResponse(jwtService.generateToken(user.get().getUsername()),
                "Bearer", jwtService.getExpiresInSeconds());
    }

    private void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Password must not exceed 72 UTF-8 bytes");
        }
    }
}
