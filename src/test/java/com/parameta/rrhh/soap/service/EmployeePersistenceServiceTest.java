package com.parameta.rrhh.soap.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.parameta.rrhh.soap.entity.EmployeeEntity;
import com.parameta.rrhh.soap.exception.DuplicateEmployeeException;
import com.parameta.rrhh.soap.repository.EmployeeRepository;
import com.parameta.rrhh.soap.service.impl.EmployeePersistenceService;
import com.parameta.rrhh.soap.soap.EmployeeType;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeePersistenceServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private EmployeePersistenceService employeePersistenceService;

    @Test
    void shouldPersistEmployee() {
        when(employeeRepository.existsByDocumentNumber("123456789")).thenReturn(false);
        when(employeeRepository.save(any(EmployeeEntity.class))).thenAnswer(invocation -> {
            EmployeeEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });

        EmployeeEntity saved = employeePersistenceService.save(sampleEmployeeType());

        assertEquals(1L, saved.getId());
        assertEquals("Juan", saved.getNames());

        ArgumentCaptor<EmployeeEntity> captor = ArgumentCaptor.forClass(EmployeeEntity.class);
        verify(employeeRepository).save(captor.capture());
        assertEquals(new BigDecimal("5000000.0"), captor.getValue().getSalary());
    }

    @Test
    void shouldRejectDuplicateDocument() {
        when(employeeRepository.existsByDocumentNumber("123456789")).thenReturn(true);

        assertThrows(DuplicateEmployeeException.class,
                () -> employeePersistenceService.save(sampleEmployeeType()));

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void shouldRejectInvalidDateFormat() {
        when(employeeRepository.existsByDocumentNumber("123456789")).thenReturn(false);
        EmployeeType employeeType = sampleEmployeeType();
        employeeType.setDateOfBirth("invalid-date");

        assertThrows(IllegalArgumentException.class,
                () -> employeePersistenceService.save(employeeType));
    }

    private EmployeeType sampleEmployeeType() {
        return new EmployeeType(
                "Juan",
                "Perez",
                "CC",
                "123456789",
                "1990-05-15",
                "2020-01-10",
                "Developer",
                5_000_000
        );
    }
}
