package com.sre.userservice.service;

import com.sre.userservice.dto.CreateUserRequest;
import com.sre.userservice.dto.UserResponse;
import com.sre.userservice.exception.DuplicateUserException;
import com.sre.userservice.exception.UserNotFoundException;
import com.sre.userservice.model.User;
import com.sre.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock  UserRepository userRepository;
    @InjectMocks UserService userService;

    private CreateUserRequest validRequest;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        validRequest = new CreateUserRequest();
        validRequest.setUserId("user-123");
        validRequest.setName("Alice");
        validRequest.setEmail("alice@example.com");
        validRequest.setPhone("+1234567890");
        idempotencyKey = UUID.randomUUID().toString();
    }

    // -- createUser -------------------------------------------------------------

    @Test
    @DisplayName("createUser - new user is persisted and returned with replay=false")
    void createUser_newUser_success() {
        when(userRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(validRequest.getEmail())).thenReturn(Optional.empty());
        User saved = buildUser();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserService.CreateResult result = userService.createUser(validRequest, idempotencyKey);

        assertThat(result.isReplay()).isFalse();
        assertThat(result.getUser().getUserId()).isEqualTo("user-123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("createUser - duplicate idempotency key returns existing user with replay=true")
    void createUser_idempotentReplay_returnsExisting() {
        User existing = buildUser();
        when(userRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existing));

        UserService.CreateResult result = userService.createUser(validRequest, idempotencyKey);

        assertThat(result.isReplay()).isTrue();
        assertThat(result.getUser().getUserId()).isEqualTo("user-123");
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("createUser - duplicate email throws DuplicateUserException")
    void createUser_duplicateEmail_throwsConflict() {
        when(userRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(validRequest.getEmail())).thenReturn(Optional.of(buildUser()));

        assertThatThrownBy(() -> userService.createUser(validRequest, idempotencyKey))
                .isInstanceOf(DuplicateUserException.class)
                .hasMessageContaining("alice@example.com");
    }

    // -- getUser ---------------------------------------------------------------

    @Test
    @DisplayName("getUser - existing user is returned")
    void getUser_found() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(buildUser()));

        UserResponse response = userService.getUser("user-123");

        assertThat(response.getUserId()).isEqualTo("user-123");
        assertThat(response.getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("getUser - unknown id throws UserNotFoundException")
    void getUser_notFound() {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getUser("unknown"))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining("unknown");
    }

    // -- helper ----------------------------------------------------------------

    private User buildUser() {
        return User.builder()
                .userId("user-123")
                .name("Alice")
                .email("alice@example.com")
                .phone("+1234567890")
                .createdAt(Instant.now())
                .idempotencyKey(idempotencyKey)
                .build();
    }
}
