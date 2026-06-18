package com.parameta.rrhh.soap.service.impl;

import com.parameta.rrhh.soap.entity.EmployeeEntity;
import com.parameta.rrhh.soap.exception.DuplicateEmployeeException;
import com.parameta.rrhh.soap.repository.EmployeeRepository;
import com.parameta.rrhh.soap.service.IEmployeePersistenceService;
import com.parameta.rrhh.soap.soap.EmployeeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists employees received from the SOAP contract.
 */
@Service
@RequiredArgsConstructor
public class EmployeePersistenceService implements IEmployeePersistenceService {

    private final EmployeeRepository employeeRepository;

    @Transactional
    public EmployeeEntity save(EmployeeType employeeType) {
        if (employeeRepository.existsByDocumentNumber(employeeType.getDocumentNumber())) {
            throw new DuplicateEmployeeException(employeeType.getDocumentNumber());
        }

        EmployeeEntity entity = new EmployeeEntity();
        entity.setNames(employeeType.getNames());
        entity.setLastNames(employeeType.getLastNames());
        entity.setTypeDocument(employeeType.getTypeDocument());
        entity.setDocumentNumber(employeeType.getDocumentNumber());
        entity.setDateOfBirth(parseDate(employeeType.getDateOfBirth()));
        entity.setDateAffiliationCompany(parseDate(employeeType.getDateAffiliationCompany()));
        entity.setPosition(employeeType.getPosition());
        entity.setSalary(BigDecimal.valueOf(employeeType.getSalary()));
        entity.setCreatedAt(LocalDateTime.now());

        return employeeRepository.save(entity);
    }

    private LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid date format: " + value);
        }
    }
}
