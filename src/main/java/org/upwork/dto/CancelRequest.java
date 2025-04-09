package org.upwork.dto;

public class CancelRequest {

    private String doctorEmailId;
    private String eventId;

    public String getDoctorEmailId() {
        return doctorEmailId;
    }

    public void setDoctorEmailId(String doctorEmailId) {
        this.doctorEmailId = doctorEmailId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }
}
