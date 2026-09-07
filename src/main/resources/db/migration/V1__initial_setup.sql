CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    inspector_id VARCHAR(50) NOT NULL UNIQUE,
    division VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    passcode VARCHAR(255) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_inspector_id (inspector_id),
    INDEX idx_user_email (email)
);

CREATE TABLE IF NOT EXISTS inspections (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_inspection_user (user_id),
    INDEX idx_inspection_status (status)
);

CREATE TABLE IF NOT EXISTS inspection_images (
    id INT AUTO_INCREMENT PRIMARY KEY,
    inspection_id INT NOT NULL,
    image_url VARCHAR(512) NOT NULL,
    type VARCHAR(50) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (inspection_id) REFERENCES inspections(id),
    INDEX idx_image_inspection (inspection_id)
);

CREATE TABLE IF NOT EXISTS product_declarations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    inspection_id INT NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    detected_value TEXT,
    is_present BOOLEAN NOT NULL,
    confidence DOUBLE DEFAULT 0.0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (inspection_id) REFERENCES inspections(id),
    INDEX idx_declaration_inspection (inspection_id)
);

CREATE TABLE IF NOT EXISTS compliance_results (
    id INT AUTO_INCREMENT PRIMARY KEY,
    inspection_id INT NOT NULL UNIQUE,
    overall_score DOUBLE NOT NULL,
    status VARCHAR(50) NOT NULL,
    analyzed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (inspection_id) REFERENCES inspections(id),
    INDEX idx_compliance_inspection (inspection_id)
);

CREATE TABLE IF NOT EXISTS violations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    compliance_result_id INT NOT NULL,
    rule_id VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    severity VARCHAR(50) NOT NULL,
    suggestion TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (compliance_result_id) REFERENCES compliance_results(id),
    INDEX idx_violation_result (compliance_result_id)
);

CREATE TABLE IF NOT EXISTS reports (
    id INT AUTO_INCREMENT PRIMARY KEY,
    inspection_id INT NOT NULL UNIQUE,
    report_url VARCHAR(512) NOT NULL,
    generated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (inspection_id) REFERENCES inspections(id),
    INDEX idx_report_inspection (inspection_id)
);
