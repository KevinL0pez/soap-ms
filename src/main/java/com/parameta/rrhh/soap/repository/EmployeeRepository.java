package com.parameta.rrhh.soap.repository;

import com.parameta.rrhh.soap.entity.EmployeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for {@link EmployeeEntity}. */
public interface EmployeeRepository extends JpaRepository<EmployeeEntity, Long> {

    boolean existsByDocumentNumber(String documentNumber);
}
