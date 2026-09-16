package com.dp.plat.lowcode.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dp.plat.lowcode.entity.LowCodeBackupRecord;
import com.dp.plat.lowcode.entity.LowCodeImportTask;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 低代码业务数据导入导出服务（缺口3）。
 *
 * <p>提供动态实体的 Excel 模板下载、异步导入、同步导出与导入历史查询能力。
 * 导入流程：上传 Excel → 创建 PENDING 任务 → {@code @Async} 异步按行插入 →
 * 更新 SUCCESS/FAILED 状态，失败行记录到 {@code failed_detail}。</p>
 *
 * <p>备份功能（{@link #createBackup}）本次仅预留空实现，实际逻辑在批次6 接入。</p>
 */
public interface LowCodeDataImportExportService extends IService<LowCodeImportTask> {

    /**
     * 下载导入模板（Excel，列头为字段 label）。
     *
     * @param entityCode 实体编码
     * @param response   HTTP 响应（写入 xlsx 流）
     */
    void downloadImportTemplate(String entityCode, HttpServletResponse response);

    /**
     * 异步导入 Excel 数据。立即返回 PENDING 任务，后续异步处理。
     *
     * @param entityCode 实体编码
     * @param file       上传的 Excel 文件
     * @return 创建的导入任务（PENDING 状态）
     */
    LowCodeImportTask importExcel(String entityCode, MultipartFile file);

    /**
     * 同步导出当前实体数据为 Excel。
     *
     * @param entityCode 实体编码
     * @param filters    过滤条件（可为 null）
     * @param response   HTTP 响应（写入 xlsx 流）
     */
    void exportExcel(String entityCode, Map<String, Object> filters, HttpServletResponse response);

    /**
     * 查询指定实体的导入任务历史。
     *
     * @param entityCode 实体编码（为 null 时查全部）
     * @return 导入任务列表（按创建时间倒序）
     */
    List<LowCodeImportTask> listImportTasks(String entityCode);

    /**
     * 查询导入任务详情（含失败明细）。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    LowCodeImportTask getImportTask(Long id);

    /**
     * 创建备份记录（预留接口，批次6实现）。
     *
     * @param type   类型 FULL/INCREMENTAL
     * @param scope  备份范围
     * @param operator 操作人
     * @return 创建的备份记录
     */
    LowCodeBackupRecord createBackup(String type, String scope, String operator);
}
