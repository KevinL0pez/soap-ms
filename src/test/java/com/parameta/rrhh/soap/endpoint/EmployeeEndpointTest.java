package com.parameta.rrhh.soap.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.parameta.rrhh.soap.entity.EmployeeEntity;
import com.parameta.rrhh.soap.service.impl.EmployeePersistenceService;
import com.parameta.rrhh.soap.soap.EmployeeType;
import com.parameta.rrhh.soap.soap.SaveEmployeeRequest;
import com.parameta.rrhh.soap.soap.SaveEmployeeResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeEndpointTest {

    @Mock
    private EmployeePersistenceService employeePersistenceService;

    @InjectMocks
    private EmployeeEndpoint employeeEndpoint;

    @Test
    void shouldReturnSaveEmployeeResponse() {
        EmployeeType employeeType = new EmployeeType(
                "Juan",
                "Perez",
                "CC",
                "123456789",
                "1990-05-15",
                "2020-01-10",
                "Developer",
                5_000_000
        );
        EmployeeEntity savedEntity = new EmployeeEntity();
        savedEntity.setId(7L);

        when(employeePersistenceService.save(employeeType)).thenReturn(savedEntity);

        SaveEmployeeResponse response = employeeEndpoint.saveEmployee(new SaveEmployeeRequest(employeeType));

        assertEquals(7L, response.getId());
        assertEquals("Employee successfully registered", response.getMessage());
    }
}
