package cn.iocoder.yudao.module.pms.platform.service.businessmodel.declared;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
@TableName("it_default_delivery_second") @Data @EqualsAndHashCode(callSuper=true)
public class DefaultDeliverySecondDO extends BaseBusinessEntity {
    private Long projectRef;
    private String title;
}
