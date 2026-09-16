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

/**
 * 低代码流程绑定实体。
 *
 * <p>绑定 Flowable 流程定义 key 到节点-表单映射 JSON，
 * 配置每个流程节点对应的表单 code 与可选微流 code。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pms_lowcode_process_binding")
public class LowCodeProcessBinding extends BaseEntity {

    /** 流程定义 key */
    @NotBlank(message = "流程定义 key 不能为空")
    @Size(max = 128, message = "流程定义 key 长度不能超过 128 个字符")
    private String processDefinitionKey;

    /** 流程定义名称 */
    @Size(max = 256, message = "流程定义名称长度不能超过 256 个字符")
    private String processDefinitionName;

    /** 节点 → 表单绑定 JSON: [{nodeId, formCode, microflowCode}] */
    @NotBlank(message = "节点表单绑定不能为空")
    private String nodeFormBindings;

    /**
     * 任务回调 JSON: {nodeId: {onCreate: microflowCode, onAssign: microflowCode, onComplete: microflowCode}}。
     *
     * <p>由 {@code ProcessTaskCallbackListener} 在 Flowable 任务事件（create/assignment/complete）
     * 时读取，触发对应微流。回调微流失败仅记日志，不阻断流程。</p>
     */
    private String taskCallbacks;

    /** 状态: ACTIVE/INACTIVE */
    @Size(max = 16, message = "状态长度不能超过 16 个字符")
    @Builder.Default
    private String status = "ACTIVE";
}
