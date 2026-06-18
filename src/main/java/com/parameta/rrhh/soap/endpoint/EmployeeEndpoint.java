package com.parameta.rrhh.soap.endpoint;

import com.parameta.rrhh.soap.entity.EmployeeEntity;
import com.parameta.rrhh.soap.service.IEmployeePersistenceService;
import com.parameta.rrhh.soap.service.impl.EmployeePersistenceService;
import com.parameta.rrhh.soap.soap.EmployeeSoapConstants;
import com.parameta.rrhh.soap.soap.SaveEmployeeRequest;
import com.parameta.rrhh.soap.soap.SaveEmployeeResponse;
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

    private final IEmployeePersistenceService iEmployeePersistenceService;

    @PayloadRoot(namespace = EmployeeSoapConstants.NAMESPACE, localPart = "saveEmployeeRequest")
    @ResponsePayload
    public SaveEmployeeResponse saveEmployee(@RequestPayload SaveEmployeeRequest request) {
        EmployeeEntity saved = iEmployeePersistenceService.save(request.getEmployee());
        return new SaveEmployeeResponse(saved.getId(), "Employee successfully registered");
    }
}
