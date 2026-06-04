package com.sre.userservice.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

//Lombok is an annotation processor that autogenerates boilerplate Java code at compile time like-
//getters, setters, constructors, builders, loggers, etc.

@Entity
@Table(
    name = "users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_email",      columnNames = "email"),
        @UniqueConstraint(name = "uk_idempotency_key", columnNames = "idempotency_key")
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder

//NoArgsCOnstructor: newUser()
//AllArgsConstructor: Constructor with all fields
//Builder: User.builder().name("...").build() pattern


public class User {

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private String userId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String phone;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Client-supplied idempotency key (UUID per logical request).
     * Unique constraint prevents duplicate submissions.
     */
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    //An idempotency key is a client-supplied UUID that makes an API request safe to retry.
    // If the client sends the same request twice (e.g., due to a network timeout),
    //  the unique constraint on this column causes the second DB insert to fail/be rejected,
    //  preventing a duplicate user from being created.
}
