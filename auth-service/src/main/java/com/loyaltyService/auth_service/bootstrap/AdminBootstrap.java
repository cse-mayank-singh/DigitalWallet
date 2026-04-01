package com.loyaltyService.auth_service.bootstrap;

import com.loyaltyService.auth_service.client.UserServiceClient;
import com.loyaltyService.auth_service.model.User;
import com.loyaltyService.auth_service.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserServiceClient userServiceClient;
    private final boolean enabled;
    private final String fullName;
    private final String email;
    private final String phone;
    private final String password;

    public AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            UserServiceClient userServiceClient,
            @Value("${app.bootstrap.admin.enabled:true}") boolean enabled,
            @Value("${app.bootstrap.admin.full-name:System Admin}") String fullName,
            @Value("${app.bootstrap.admin.email:admin@digitalwallet.local}") String email,
            @Value("${app.bootstrap.admin.phone:9999999999}") String phone,
            @Value("${app.bootstrap.admin.password:Admin@123}") String password
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.userServiceClient = userServiceClient;
        this.enabled = enabled;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Admin bootstrap is disabled");
            return;
        }

        if (userRepository.existsByRole(User.Role.ADMIN)) {
            log.info("Admin bootstrap skipped because an admin already exists");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();
        String normalizedPhone = phone.trim();

        if (userRepository.existsByEmail(normalizedEmail)) {
            log.warn("Admin bootstrap skipped because email {} is already in use", normalizedEmail);
            return;
        }

        if (userRepository.existsByPhone(normalizedPhone)) {
            log.warn("Admin bootstrap skipped because phone {} is already in use", maskPhone(normalizedPhone));
            return;
        }

        User admin = userRepository.save(User.builder()
                .fullName(fullName.trim())
                .email(normalizedEmail)
                .phone(normalizedPhone)
                .password(passwordEncoder.encode(password))
                .role(User.Role.ADMIN)
                .isActive(true)
                .build());

        userServiceClient.createUser(new UserServiceClient.CreateUserRequest(
                admin.getId(),
                admin.getFullName(),
                admin.getEmail(),
                admin.getPhone(),
                admin.getRole()
        ));

        log.warn("Bootstrap admin created with email={}. Change the bootstrap password after first login.", admin.getEmail());
    }

    private String maskPhone(String value) {
        if (value == null || value.length() < 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }
}
