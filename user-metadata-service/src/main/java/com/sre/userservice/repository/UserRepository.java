package com.sre.userservice.repository;

import com.sre.userservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByIdempotencyKey(String idempotencyKey);
    Optional<User> findByEmail(String email);
}
