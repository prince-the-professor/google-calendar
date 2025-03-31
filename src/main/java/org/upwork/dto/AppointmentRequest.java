package org.upwork.dto;

public class AppointmentRequest {
    private String firstName;
    private String lastName;
    private String email;
    private String startTime; // e.g. "2024-04-01T10:00:00+05:30"

    // Getters and Setters
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }
}