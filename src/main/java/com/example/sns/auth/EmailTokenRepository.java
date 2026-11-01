package com.example.sns.auth;

import com.example.sns.user.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    Optional<EmailToken> findByTokenHashAndPurpose(String tokenHash, EmailToken.Purpose purpose);

    boolean existsByUserAndPurposeAndCreatedAtAfter(User user, EmailToken.Purpose purpose, Instant since);

    void deleteByUser(User user);
}
