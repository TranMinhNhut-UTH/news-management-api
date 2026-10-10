package com.uth.news.service;

import com.uth.news.dto.request.*;
import com.uth.news.entity.User;
import com.uth.news.exception.*;
import com.uth.news.repository.UserRepository;
import com.uth.news.security.JwtService;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private UserRepository repository;
    private BCryptPasswordEncoder encoder;
    private AuthService service;

    @BeforeEach
    void setup() {
        repository = mock(UserRepository.class);
        encoder = new BCryptPasswordEncoder();
        service = new AuthService(repository, encoder,
                new JwtService("test-only-secret-0123456789-abcdef", 3600000));
    }

    @Test
    void registerStoresBcryptHashAndReturnsOnlySafeUserData() {
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var response = service.register(new RegisterRequest("writer", "password123"));
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(captor.capture());
        String hash = captor.getValue().getPasswordHash();
        assertThat(hash).startsWith("$2a$").isNotEqualTo("password123");
        assertThat(encoder.matches("password123", hash)).isTrue();
        assertThat(response.username()).isEqualTo("writer");
    }

    @Test
    void duplicateUsernameIs409WithoutWriting() {
        when(repository.existsByUsername("writer")).thenReturn(true);
        assertThatThrownBy(() -> service.register(new RegisterRequest("writer", "password123")))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void databaseUniqueRaceIs409() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate"));
        assertThatThrownBy(() -> service.register(new RegisterRequest("writer", "password123")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void wrongPasswordAndUnknownUserHaveSame401Message() {
        when(repository.findByUsername("writer")).thenReturn(Optional.of(
                new User("writer", encoder.encode("password123"))));
        when(repository.findByUsername("missing")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginRequest("writer", "wrong")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid username or password");
        assertThatThrownBy(() -> service.login(new LoginRequest("missing", "wrong")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("Invalid username or password");
    }

    @Test
    void utf8PasswordOverBcryptLimitIs400ForBothOperations() {
        String password = "é".repeat(37);
        assertThatThrownBy(() -> service.register(new RegisterRequest("writer", password)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.login(new LoginRequest("writer", password)))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(repository);
    }
}
