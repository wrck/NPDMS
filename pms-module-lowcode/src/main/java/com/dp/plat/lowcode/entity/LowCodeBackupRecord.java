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
 * 低代码数据备份记录实体（缺口3）。
 *
 * <p><b>本次仅建表预留，备份功能在批次6实现</b>。表结构已就位，
 * Service 层提供空方法占位，待批次6 接入实际备份逻辑。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_backup_record")
public class LowCodeBackupRecord extends BaseEntity {

    /** 类型: FULL/INCREMENTAL */
    @NotBlank(message = "类型不能为空")
    @Size(max = 16, message = "类型长度不能超过 16 个字符")
    private String type;

    /** 备份范围（实体编码/全部） */
    @Size(max = 128, message = "范围长度不能超过 128 个字符")
    private String scope;

    /** 备份文件路径 */
    @NotBlank(message = "文件路径不能为空")
    @Size(max = 512, message = "文件路径长度不能超过 512 个字符")
    private String filePath;

    /** 文件大小（字节） */
    @Builder.Default
    private Long fileSize = 0L;

    /** 状态: PENDING/RUNNING/SUCCESS/FAILED */
    @NotBlank(message = "状态不能为空")
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "PENDING";

    /** 操作人 */
    @Size(max = 64, message = "操作人长度不能超过 64 个字符")
    private String operator;

    /** 备份时间 */
    private LocalDateTime backupTime;

    /** 过期时间 */
    private LocalDateTime expireAt;
}
