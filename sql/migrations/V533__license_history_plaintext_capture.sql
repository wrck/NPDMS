-- 2026-11-02: approved BASE_P37 plaintext capture. Existing encrypted history is retained.
ALTER TABLE ast_device_license_record
    ADD COLUMN license_code VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '许可来源原文；新P37按批准规则直接保存',
    MODIFY COLUMN license_code_ciphertext TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    ADD CONSTRAINT chk_license_record_single_content CHECK (
        (license_code IS NOT NULL AND license_code_ciphertext IS NULL)
        OR (license_code IS NULL AND license_code_ciphertext IS NOT NULL)
    );
