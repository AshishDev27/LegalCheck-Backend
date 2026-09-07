-- The OCR text a device reads off a panel is stored beside the image it came from, so
-- POST /inspections/{id}/extract can rebuild the product from both panels without the
-- device having to re-send them. InspectionImageTable has declared this column since the
-- scan pipeline was introduced; V1 never created it.
ALTER TABLE inspection_images
    ADD COLUMN ocr_text TEXT NULL AFTER type;

-- QrResultTable is referenced by InspectionRepository.saveQrResult but no migration ever
-- created its table, so POST /inspections/{id}/qr failed the same way the upload did.
CREATE TABLE IF NOT EXISTS qr_results (
    id INT AUTO_INCREMENT PRIMARY KEY,
    inspection_id INT NOT NULL,
    code_type VARCHAR(100) NOT NULL,
    raw_value TEXT NOT NULL,
    format VARCHAR(100) NOT NULL,
    source_image_id INT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (inspection_id) REFERENCES inspections(id),
    FOREIGN KEY (source_image_id) REFERENCES inspection_images(id),
    INDEX idx_qr_inspection (inspection_id)
);
