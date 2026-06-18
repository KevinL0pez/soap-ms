package com.parameta.rrhh.soap.exception;

import org.springframework.ws.soap.server.endpoint.annotation.FaultCode;
import org.springframework.ws.soap.server.endpoint.annotation.SoapFault;

/** Raised when the document number already exists in the database. */
@SoapFault(faultCode = FaultCode.CLIENT)
public class DuplicateEmployeeException extends RuntimeException {

    public DuplicateEmployeeException(String documentNumber) {
        super("An employee with document number already exists: " + documentNumber);
    }
}
