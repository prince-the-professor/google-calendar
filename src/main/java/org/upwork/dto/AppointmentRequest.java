package org.upwork.dto;

import lombok.Data;

@Data
public class AppointmentRequest {
    private String firstName;
    private String lastName;
    private String doctorEmail;
    private String patientEmail;
    private String startTime; // e.g. "2024-04-01T10:00:00+05:30"

}