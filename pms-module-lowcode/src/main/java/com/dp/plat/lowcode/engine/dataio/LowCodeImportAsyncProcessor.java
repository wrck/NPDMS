package com.dp.plat.lowcode.engine.dataio;

import com.dp.plat.lowcode.dto.EntityDesignDTO;
import com.dp.plat.lowcode.engine.DynamicEntityDataService;
import com.dp.plat.lowcode.entity.LowCodeField;
import com.dp.plat.lowcode.entity.LowCodeImportTask;
import com.dp.plat.lowcode.mapper.LowCodeImportTaskMapper;
import com.dp.plat.lowcode.service.LowCodeEntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dp.plat.lowcode.entity.LowCodeEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 异步导入处理器（缺口3）。
 *
 * <p>独立 {@code @Component} 承载 {@code @Async} 方法，避免同类自调用导致
 * Spring AOP 代理失效的问题（{@code this.processImportAsync(...)} 不会走代理，
 * {@code @Async} 不生效）。</p>
 *
 * <p>由 {@code LowCodeDataImportExportServiceImpl#importExcel} 委托调用，
 * 在独立线程中完成 Excel 解析、按行 create、失败明细记录与任务状态更新。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LowCodeImportAsyncProcessor {

    private final LowCodeEntityService entityService;
    private final DynamicEntityDataService dynamicEntityDataService;
    private final LowCodeImportTaskMapper importTaskMapper;
    private final ObjectMapper objectMapper;

    /**
     * 异步执行实际导入：解析 Excel → 按行 create → 更新任务状态。
     *
     * @param taskId     导入任务 ID
     * @param entityCode 实体编码
     * @param file       上传的 Excel 文件
     */
    @Async
    public void processImportAsync(Long taskId, String entityCode, MultipartFile file) {
        LowCodeImportTask task = importTaskMapper.selectById(taskId);
        if (task == null) return;
        task.setStatus("RUNNING");
        task.setStartTime(LocalDateTime.now());
        importTaskMapper.updateById(task);

        List<Map<String, Object>> failedDetails = new ArrayList<>();
        int success = 0;
        int failed = 0;
        try {
            EntityDesignDTO design = getDesign(entityCode);
            List<LowCodeField> fields = design.getFields();
            Map<String, String> labelToName = new LinkedHashMap<>();
            for (LowCodeField f : fields) {
                labelToName.put(f.getLabel(), f.getName());
            }

            try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
                Sheet sheet = workbook.getSheetAt(0);
                int lastRow = sheet.getLastRowNum();
                task.setTotalRows(Math.max(0, lastRow));
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) {
                    throw new IllegalStateException("Excel 表头为空");
                }
                List<String> headerLabels = new ArrayList<>();
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    Cell cell = headerRow.getCell(c);
                    headerLabels.add(cell == null ? "" : cell.getStringCellValue());
                }
                for (int r = 1; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) continue;
                    Map<String, Object> data = new LinkedHashMap<>();
                    try {
                        for (int c = 0; c < headerLabels.size(); c++) {
                            String label = headerLabels.get(c);
                            String fieldName = labelToName.get(label);
                            if (fieldName == null) continue;
                            Cell cell = row.getCell(c);
                            data.put(fieldName, readCell(cell));
                        }
                        dynamicEntityDataService.create(entityCode, data);
                        success++;
                    } catch (Exception e) {
                        failed++;
                        Map<String, Object> detail = new LinkedHashMap<>();
                        detail.put("row", r + 1);
                        detail.put("error", e.getMessage());
                        failedDetails.add(detail);
                    }
                }
            }
            task.setSuccessRows(success);
            task.setFailedRows(failed);
            task.setFailedDetail(failedDetails.isEmpty() ? null
                    : objectMapper.writeValueAsString(failedDetails));
            // 部分成功视为 SUCCESS（任务完成），仅全部失败才 FAILED
            task.setStatus(failed == 0 ? "SUCCESS" : (success == 0 ? "FAILED" : "SUCCESS"));
            if (success == 0 && failed > 0) {
                task.setErrorMessage("全部 " + failed + " 行导入失败");
            }
        } catch (Exception e) {
            log.error("[DataImport] 异步导入任务失败: taskId={}, entityCode={}", taskId, entityCode, e);
            task.setStatus("FAILED");
            task.setErrorMessage(truncate(e.getMessage(), 512));
            task.setSuccessRows(success);
            task.setFailedRows(failed);
            try {
                task.setFailedDetail(failedDetails.isEmpty() ? null
                        : objectMapper.writeValueAsString(failedDetails));
            } catch (Exception ignore) {
                // ignore
            }
        } finally {
            task.setEndTime(LocalDateTime.now());
            importTaskMapper.updateById(task);
        }
    }

    private EntityDesignDTO getDesign(String entityCode) {
        LowCodeEntity entity = entityService.getOne(new LambdaQueryWrapper<LowCodeEntity>()
                .eq(LowCodeEntity::getCode, entityCode));
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在: " + entityCode);
        }
        return entityService.getDesign(entity.getId());
    }

    private Object readCell(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> cell.getNumericCellValue();
            case BOOLEAN -> cell.getBooleanCellValue();
            case FORMULA -> cell.getCellFormula();
            default -> null;
        };
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
