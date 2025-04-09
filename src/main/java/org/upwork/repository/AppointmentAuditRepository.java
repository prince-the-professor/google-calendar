package org.upwork.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.upwork.entity.AppointmentAudit;

import java.util.List;

@Repository
public interface AppointmentAuditRepository extends JpaRepository<AppointmentAudit, Long> {
    List<AppointmentAudit> findByDoctorEmail(String doctorEmail);
    List<AppointmentAudit> findByPatientEmail(String patientEmail);
}