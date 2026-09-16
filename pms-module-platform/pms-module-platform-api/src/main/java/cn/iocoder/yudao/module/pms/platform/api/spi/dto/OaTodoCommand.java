package cn.iocoder.yudao.module.pms.platform.api.spi.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * OA 待办命令。
 *
 * <p>仅包含 workflow 发布待办意图与 integration 执行 OA 适配共同需要的稳定字段，
 * 不暴露任一实现模块的内部模型。</p>
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

    /** 业务键。 */
    private String businessKey;

    /** 流程详情 URL。 */
    private String processUrl;

    /** 业务类型。 */
    private String businessType;
}
