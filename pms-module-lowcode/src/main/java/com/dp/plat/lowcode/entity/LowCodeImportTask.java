package com.dp.plat.lowcode.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.dp.plat.common.entity.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 低代码数据导入任务实体（缺口3）。
 *
 * <p>记录异步 Excel 导入任务的执行状态、行数统计与失败明细。
 * 任务由 {@code LowCodeDataImportExportService.importExcel} 创建为 PENDING，
 * 通过 {@code @Async} 异步处理，最终更新为 SUCCESS/FAILED。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_import_task")
public class LowCodeImportTask extends BaseEntity {

    /** 实体编码 */
    @NotBlank(message = "实体编码不能为空")
    @Size(max = 64, message = "实体编码长度不能超过 64 个字符")
    private String entityCode;

    /** 上传文件名 */
    @NotBlank(message = "文件名不能为空")
    @Size(max = 256, message = "文件名长度不能超过 256 个字符")
    private String fileName;

    /** 状态: PENDING/RUNNING/SUCCESS/FAILED */
    @NotBlank(message = "状态不能为空")
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "PENDING";

    /** 总行数 */
    @Builder.Default
    private Integer totalRows = 0;

    /** 成功行数 */
    @Builder.Default
    private Integer successRows = 0;

    /** 失败行数 */
    @Builder.Default
    private Integer failedRows = 0;

    /** 失败明细 JSON: [{row, field, error}] */
    private String failedDetail;

    /** 任务级错误信息 */
    @Size(max = 512, message = "错误信息长度不能超过 512 个字符")
    private String errorMessage;

    /** 操作人 */
    @Size(max = 64, message = "操作人长度不能超过 64 个字符")
    private String operator;

    /** 任务开始时间 */
    private LocalDateTime startTime;

    /** 任务结束时间 */
    private LocalDateTime endTime;
}
