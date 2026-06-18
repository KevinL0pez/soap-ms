package com.parameta.rrhh.soap.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {"id", "message"})
@XmlRootElement(name = "saveEmployeeResponse", namespace = EmployeeSoapConstants.NAMESPACE)
public class SaveEmployeeResponse {

    @XmlElement(namespace = EmployeeSoapConstants.NAMESPACE)
    private long id;

    @XmlElement(required = true, namespace = EmployeeSoapConstants.NAMESPACE)
    private String message;
}
