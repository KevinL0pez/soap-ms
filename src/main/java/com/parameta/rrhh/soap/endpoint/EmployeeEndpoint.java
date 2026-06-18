package com.parameta.rrhh.soap.endpoint;

import com.parameta.rrhh.soap.entity.EmployeeEntity;
import com.parameta.rrhh.soap.service.EmployeePersistenceService;
import com.parameta.rrhh.soap.soap.EmployeeSoapConstants;
import com.parameta.rrhh.soap.soap.SaveEmployeeRequest;
import com.parameta.rrhh.soap.soap.SaveEmployeeResponse;
import com.parameta.rrhh.soap.soap.EmployeeType;
import lombok.RequiredArgsConstructor;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

/**
 * SOAP endpoint that persists validated employees.
 */
@Endpoint
@RequiredArgsConstructor
public class EmployeeEndpoint {

    private final EmployeePersistenceService employeePersistenceService;

    @PayloadRoot(namespace = EmployeeSoapConstants.NAMESPACE, localPart = "saveEmployeeRequest")
    @ResponsePayload
    public SaveEmployeeResponse saveEmployee(@RequestPayload SaveEmployeeRequest request) {
        EmployeeEntity saved = employeePersistenceService.save(request.getEmployee());
        return new SaveEmployeeResponse(saved.getId(), "Employee successfully registered");
    }
}
