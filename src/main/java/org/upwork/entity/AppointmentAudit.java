package org.upwork.entity;

import lombok.Data;

import javax.persistence.*;
import java.time.ZonedDateTime;

@Data
@Entity
@Table(name = "appointment_audit")
public class AppointmentAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String doctorEmail;

    private String patientEmail;

    private String firstName;

    private String lastName;

    private ZonedDateTime slotStart;

    private ZonedDateTime slotEnd;

    private String eventId;

    private String status;

    private ZonedDateTime createdAt = ZonedDateTime.now();

    // Getters and Setters
}