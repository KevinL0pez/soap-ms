package com.parameta.rrhh.soap.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** JPA mapping for the {@code employee} table. */
@Getter
@Setter
@Entity
@Table(name = "employee")
public class EmployeeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "names", nullable = false, length = 100)
    private String names;

    @Column(name = "last_names", nullable = false, length = 100)
    private String lastNames;

    @Column(name = "type_document", nullable = false, length = 20)
    private String typeDocument;

    @Column(name = "document_number", nullable = false, length = 50, unique = true)
    private String documentNumber;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "date_affiliation_company", nullable = false)
    private LocalDate dateAffiliationCompany;

    @Column(name = "position", nullable = false, length = 100)
    private String position;

    @Column(name = "salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal salary;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
