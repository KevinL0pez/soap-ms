CREATE TABLE employee (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    names VARCHAR(100) NOT NULL,
    last_names VARCHAR(100) NOT NULL,
    type_document VARCHAR(20) NOT NULL,
    document_number VARCHAR(50) NOT NULL,
    date_of_birth DATE NOT NULL,
    date_affiliation_company DATE NOT NULL,
    position VARCHAR(100) NOT NULL,
    salary DECIMAL(15, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_employee_document_number UNIQUE (document_number)
);
