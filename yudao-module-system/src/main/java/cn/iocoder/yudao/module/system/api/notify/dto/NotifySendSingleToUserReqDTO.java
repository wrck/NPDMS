package cn.iocoder.yudao.module.system.api.notify.dto;

import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * 站内信发送给 Admin 或者 Member 用户
 *
 * @author xrcoder
 */
@Data
public class NotifySendSingleToUserReqDTO {

    /**
     * 用户编号
     */
    @NotNull(message = "用户编号不能为空")
    private Long userId;

    /**
     * 站内信模板编号
     */
    @NotEmpty(message = "站内信模板编号不能为空")
    private String templateCode;

    /**
     * 站内信模板参数
     */
    private Map<String, Object> templateParams;

    /**
     * 业务投递幂等键；既有调用可不传
     */
    @Size(max = 128, message = "站内信投递键长度不能超过128个字符")
    private String deliveryKey;
}
