package com.parameta.rrhh.soap.service;

import com.parameta.rrhh.soap.entity.EmployeeEntity;
import com.parameta.rrhh.soap.soap.EmployeeType;

public interface IEmployeePersistenceService {

    EmployeeEntity save(EmployeeType employeeType);

}
