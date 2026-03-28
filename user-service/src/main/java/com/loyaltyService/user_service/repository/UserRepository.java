package com.loyaltyService.user_service.repository;

import com.loyaltyService.user_service.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    boolean existsByEmail(String email);

    // ─── Admin filters ────────────────────────────────────────────────────────

    Page<User> findByStatus(User.UserStatus status, Pageable pageable);

    Page<User> findByRole(User.Role role, Pageable pageable);

    Page<User> findByStatusAndRole(User.UserStatus status, User.Role role, Pageable pageable);

    /** Keyword search across name, email, phone */
    @Query("""
        SELECT u FROM User u
        WHERE LOWER(u.name)  LIKE LOWER(CONCAT('%', :q, '%'))
           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
           OR u.phone        LIKE CONCAT('%', :q, '%')
        """)
    Page<User> searchByKeyword(@Param("q") String q, Pageable pageable);

    /** Registration date range */
    Page<User> findByCreatedAtBetween(Instant from, Instant to, Pageable pageable);

    /** Users whose latest KYC record matches the given status */
    @Query("""
    SELECT u FROM User u
    JOIN KycDetail k ON k.user.id = u.id
    WHERE k.submittedAt = (
        SELECT MAX(k2.submittedAt)
        FROM KycDetail k2
        WHERE k2.user.id = u.id
    )
    AND k.status = :kycStatus
""")
    Page<User> findByLatestKycStatus(@Param("kycStatus") String kycStatus, Pageable pageable);

    // Dashboard counts
    long countByStatus(User.UserStatus status);

    long countByRole(User.Role role);

    long countByCreatedAtAfter(Instant since);

    long countByCreatedAtBetween(Instant from, Instant to);
}
