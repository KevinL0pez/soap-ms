package com.parameta.rrhh.soap.soap;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
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
@XmlType(name = "EmployeeType", namespace = EmployeeSoapConstants.NAMESPACE)
public class EmployeeType {

    @XmlElement(required = true)
    private String names;

    @XmlElement(required = true)
    private String lastNames;

    @XmlElement(required = true)
    private String typeDocument;

    @XmlElement(required = true)
    private String documentNumber;

    @XmlElement(required = true)
    private String dateOfBirth;

    @XmlElement(required = true)
    private String dateAffiliationCompany;

    @XmlElement(required = true)
    private String position;

    @XmlElement(required = true)
    private double salary;
}
