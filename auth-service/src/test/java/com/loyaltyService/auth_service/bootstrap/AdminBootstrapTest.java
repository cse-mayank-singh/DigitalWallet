package com.loyaltyService.auth_service.bootstrap;

import com.loyaltyService.auth_service.client.UserServiceClient;
import com.loyaltyService.auth_service.model.User;
import com.loyaltyService.auth_service.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserServiceClient userServiceClient;

    @Test
    void runCreatesBootstrapAdminWhenMissing() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                userServiceClient,
                true,
                "Bootstrap Admin",
                "ADMIN@EXAMPLE.COM",
                "9876543210",
                "Admin@123"
        );

        when(userRepository.existsByRole(User.Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("9876543210")).thenReturn(false);
        when(passwordEncoder.encode("Admin@123")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("Bootstrap Admin", savedUser.getFullName());
        assertEquals("admin@example.com", savedUser.getEmail());
        assertEquals("9876543210", savedUser.getPhone());
        assertEquals("encoded-password", savedUser.getPassword());
        assertSame(User.Role.ADMIN, savedUser.getRole());

        verify(userServiceClient).createUser(new UserServiceClient.CreateUserRequest(
                10L, "Bootstrap Admin", "admin@example.com", "9876543210", User.Role.ADMIN));
    }

    @Test
    void runSkipsWhenAdminAlreadyExists() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                userServiceClient,
                true,
                "Bootstrap Admin",
                "admin@example.com",
                "9876543210",
                "Admin@123"
        );

        when(userRepository.existsByRole(User.Role.ADMIN)).thenReturn(true);

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository, never()).save(any(User.class));
        verify(userServiceClient, never()).createUser(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void runSkipsWhenBootstrapIsDisabled() throws Exception {
        AdminBootstrap bootstrap = new AdminBootstrap(
                userRepository,
                passwordEncoder,
                userServiceClient,
                false,
                "Bootstrap Admin",
                "admin@example.com",
                "9876543210",
                "Admin@123"
        );

        bootstrap.run(new DefaultApplicationArguments(new String[0]));

        verify(userRepository, never()).existsByRole(any());
        verify(userRepository, never()).save(any(User.class));
        verify(userServiceClient, never()).createUser(any());
        verify(passwordEncoder, never()).encode(any());
    }
}
