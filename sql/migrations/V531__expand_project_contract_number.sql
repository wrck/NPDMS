-- Allow the project contract-number field to hold comma-separated contract numbers.
ALTER TABLE proj_project
    MODIFY COLUMN contract_no VARCHAR(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci
        NULL COMMENT '手工登记合同号（正式商业关系随 INT-02 接管；前向扩列）',
    ALGORITHM=INPLACE, LOCK=NONE;
