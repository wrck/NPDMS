package com.dp.plat.lowcode.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.plat.common.util.SecurityUtils;
import com.dp.plat.lowcode.dto.EntityDesignDTO;
import com.dp.plat.lowcode.engine.DynamicEntityDataService;
import com.dp.plat.lowcode.engine.dataio.LowCodeImportAsyncProcessor;
import com.dp.plat.lowcode.entity.LowCodeBackupRecord;
import com.dp.plat.lowcode.entity.LowCodeEntity;
import com.dp.plat.lowcode.entity.LowCodeField;
import com.dp.plat.lowcode.entity.LowCodeImportTask;
import com.dp.plat.lowcode.mapper.LowCodeBackupRecordMapper;
import com.dp.plat.lowcode.mapper.LowCodeImportTaskMapper;
import com.dp.plat.lowcode.service.LowCodeDataImportExportService;
import com.dp.plat.lowcode.service.LowCodeEntityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 低代码业务数据导入导出服务实现（缺口3）。
 *
 * <p><b>Excel 引擎</b>：使用 Apache POI（XSSFWorkbook）直接操作 .xlsx，
 * 列头为字段 label，按字段顺序生成列。导入时按列头匹配字段 name。</p>
 *
 * <p><b>异步导入</b>：{@link #importExcel} 创建 PENDING 任务后立即返回，
 * {@link #processImportAsync} 通过 {@code @Async} 异步执行实际解析与按行插入。
 * 失败行记录到 {@code failed_detail} JSON 数组。</p>
 *
 * <p><b>备份预留</b>：{@link #createBackup} 本次仅插入占位记录，实际备份逻辑
 * 在批次6 接入（待对接对象存储 / OSS）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LowCodeDataImportExportServiceImpl
        extends ServiceImpl<LowCodeImportTaskMapper, LowCodeImportTask>
        implements LowCodeDataImportExportService {

    private final LowCodeEntityService entityService;
    private final DynamicEntityDataService dynamicEntityDataService;
    private final LowCodeImportAsyncProcessor importAsyncProcessor;
    private final LowCodeImportTaskMapper importTaskMapper;
    private final LowCodeBackupRecordMapper backupRecordMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void downloadImportTemplate(String entityCode, HttpServletResponse response) {
        EntityDesignDTO design = getDesign(entityCode);
        List<LowCodeField> fields = design.getFields();
        String fileName = entityCode + "-import-template.xlsx";
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(entityCode);
            // 表头样式（加粗）
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            // 写表头（字段 label）
            Row header = sheet.createRow(0);
            for (int i = 0; i < fields.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(fields.get(i).getLabel());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 20 * 256);
            }
            writeWorkbookToResponse(workbook, fileName, response);
        } catch (IOException e) {
            throw new RuntimeException("下载导入模板失败: " + e.getMessage(), e);
        }
    }

    @Override
    public LowCodeImportTask importExcel(String entityCode, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("导入文件不能为空");
        }
        LowCodeImportTask task = LowCodeImportTask.builder()
                .entityCode(entityCode)
                .fileName(file.getOriginalFilename())
                .status("PENDING")
                .totalRows(0)
                .successRows(0)
                .failedRows(0)
                .operator(SecurityUtils.getCurrentUsername())
                .build();
        importTaskMapper.insert(task);
        // 委托独立 @Component 异步处理（避免同类自调用绕过 @Async 代理）
        importAsyncProcessor.processImportAsync(task.getId(), entityCode, file);
        return task;
    }

    /**
     * 异步处理已委托至 {@link LowCodeImportAsyncProcessor#processImportAsync}，
     * 避免同类自调用导致 Spring AOP {@code @Async} 代理失效。
     */

    @Override
    public void exportExcel(String entityCode, Map<String, Object> filters, HttpServletResponse response) {
        EntityDesignDTO design = getDesign(entityCode);
        List<LowCodeField> fields = design.getFields();
        // 拉取数据（取前 10000 行，避免超大导出 OOM）
        Map<String, Object> listResult = dynamicEntityDataService.list(entityCode, 1, 10000, filters);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) listResult.get("records");
        if (records == null) records = new ArrayList<>();

        String fileName = entityCode + "-export.xlsx";
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(entityCode);
            // 表头
            Row header = sheet.createRow(0);
            for (int i = 0; i < fields.size(); i++) {
                header.createCell(i).setCellValue(fields.get(i).getLabel());
                sheet.setColumnWidth(i, 20 * 256);
            }
            // 数据行
            for (int r = 0; r < records.size(); r++) {
                Row row = sheet.createRow(r + 1);
                Map<String, Object> rec = records.get(r);
                for (int c = 0; c < fields.size(); c++) {
                    Object v = rec.get(fields.get(c).getName());
                    Cell cell = row.createCell(c);
                    if (v != null) cell.setCellValue(v.toString());
                }
            }
            writeWorkbookToResponse(workbook, fileName, response);
        } catch (IOException e) {
            throw new RuntimeException("导出 Excel 失败: " + e.getMessage(), e);
        }
    }

    @Override
    public List<LowCodeImportTask> listImportTasks(String entityCode) {
        LambdaQueryWrapper<LowCodeImportTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(entityCode != null && !entityCode.isBlank(),
                        LowCodeImportTask::getEntityCode, entityCode)
                .orderByDesc(LowCodeImportTask::getCreateTime)
                .last("LIMIT 200");
        return importTaskMapper.selectList(wrapper);
    }

    @Override
    public LowCodeImportTask getImportTask(Long id) {
        return importTaskMapper.selectById(id);
    }

    @Override
    public LowCodeBackupRecord createBackup(String type, String scope, String operator) {
        // 备份功能在批次6 实现，本次仅建表预留并插入占位记录
        LowCodeBackupRecord record = LowCodeBackupRecord.builder()
                .type(type)
                .scope(scope)
                .filePath("pending-batch6")
                .fileSize(0L)
                .status("PENDING")
                .operator(operator)
                .build();
        backupRecordMapper.insert(record);
        log.info("[DataBackup] 备份记录已创建占位（实际备份逻辑在批次6实现）: type={}, scope={}", type, scope);
        return record;
    }

    // ==================== 工具方法 ====================

    private EntityDesignDTO getDesign(String entityCode) {
        LowCodeEntity entity = entityService.getOne(
                new LambdaQueryWrapper<LowCodeEntity>()
                        .eq(LowCodeEntity::getCode, entityCode));
        if (entity == null) {
            throw new IllegalArgumentException("实体不存在: " + entityCode);
        }
        return entityService.getDesign(entity.getId());
    }

    /** 将 Workbook 写入 HTTP 响应（含 Content-Disposition） */
    private void writeWorkbookToResponse(Workbook workbook, String fileName, HttpServletResponse response)
            throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        try (OutputStream out = response.getOutputStream()) {
            workbook.write(out);
            out.flush();
        }
    }
}
