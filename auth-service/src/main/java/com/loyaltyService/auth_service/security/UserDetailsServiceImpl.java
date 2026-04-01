package com.loyaltyService.auth_service.security;


import com.loyaltyService.auth_service.client.UserQueryClient;
import com.loyaltyService.auth_service.model.User;
import com.loyaltyService.auth_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserQueryClient userQueryClient;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String usernameOrPhone) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(usernameOrPhone.toLowerCase())
                .orElseGet(() -> userRepository.findByPhone(usernameOrPhone)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found")));

        // Admin blocks users in user-service (`users.status` = ACTIVE/BLOCKED).
        // auth-service uses its own `isActive`, so we consult user-service during authentication.
        boolean enabled = user.isActive();
        try {
            UserQueryClient.UserProfileResponse profile = userQueryClient.getProfile(user.getId());
            if (profile != null && profile.status() != null) {
                enabled = "ACTIVE".equalsIgnoreCase(profile.status());
            }
        } catch (Exception ex) {
            // If user-service is down, fall back to local flag to avoid hard lockouts.
            log.warn("Could not fetch user status from user-service for userId={}. Using local isActive={}",
                    user.getId(), user.isActive(), ex);
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                enabled,
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
    }
}