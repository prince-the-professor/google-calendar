package org.upwork.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.upwork.entity.DoctorCredential;

@Repository
public interface DoctorCredentialRepository extends JpaRepository<DoctorCredential, Long> {
    DoctorCredential findByEmail(String email);
}