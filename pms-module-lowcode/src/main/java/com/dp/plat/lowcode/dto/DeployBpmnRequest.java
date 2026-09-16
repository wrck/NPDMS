package com.dp.plat.lowcode.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * BPMN XML 部署请求。
 *
 * <p>低代码流程设计器在前端通过 bpmn-js 导出 BPMN 2.0 XML 字符串后，
 * 以 JSON 形式提交到 {@code POST /api/lowcode/process/deploy}，由后端转换为
 * MultipartFile 调用 {@code WorkflowService.deployProcess} 部署到 Flowable。</p>
 */
@Data
public class DeployBpmnRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** BPMN 2.0 XML 内容 */
    @NotBlank(message = "BPMN XML 不能为空")
    private String xml;

    /** 流程名称（用作部署名与资源文件名） */
    @NotBlank(message = "流程名称不能为空")
    private String name;
}
