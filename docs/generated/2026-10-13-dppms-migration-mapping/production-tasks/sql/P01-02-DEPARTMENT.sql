-- P01 / DEPARTMENT | 原任务 2101562667583217666 v16
-- TABLE_COMPILED_EQUIVALENT；只读整理，未执行；当前参数和完整业务映射见对应task JSON。
-- TABLE原位置参数改用具名参数，谓词和值保持；{"filter0": "2", "filter1": "6"}
SELECT `depID`, `depCode`, `depName`, `isDisabled`, `adminID`, `compID`, `depGrade` FROM `ehr_department` WHERE `depGrade` <= :filter0 AND LENGTH(`depCode`) = :filter1;
