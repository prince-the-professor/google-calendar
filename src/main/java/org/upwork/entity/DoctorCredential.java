package org.upwork.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "doctor_credentials")
public class DoctorCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String doctorId;

    @Column(unique = true)
    private String email;

    @Lob
    private String accessToken;

    @Lob
    private String refreshToken;

    private Long tokenExpiry;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Getters and Setters
}