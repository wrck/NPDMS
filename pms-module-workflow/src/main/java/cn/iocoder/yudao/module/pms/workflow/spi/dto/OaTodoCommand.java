package cn.iocoder.yudao.module.pms.workflow.spi.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * OA 待办命令（本模块 SPI 配套，迁移自源工程 pms-integration com.dp.plat.integration.model.oa.OaTodoRequest
 * 在 OaTaskListener 场景下使用的字段）。
 */
@Data
@Builder
public class OaTodoCommand implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 待办标题。 */
    private String title;

    /** 待办内容。 */
    private String content;

    /** 处理人用户 ID。 */
    private String handlerUserId;

    /** 流程实例 ID。 */
    private String processInstanceId;

    /** 业务键：兼容流程变量 businessKey，未提供时使用 Flowable 任务 ID。 */
    private String businessKey;

    /** 流程详情 URL。 */
    private String processUrl;

    /** 业务类型（Flowable 场景下为流程定义 ID）。 */
    private String businessType;
}
