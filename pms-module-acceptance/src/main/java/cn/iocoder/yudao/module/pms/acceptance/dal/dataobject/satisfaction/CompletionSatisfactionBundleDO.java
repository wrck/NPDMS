package cn.iocoder.yudao.module.pms.acceptance.dal.dataobject.satisfaction;

import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Immutable pairing of one completion certificate and one satisfaction collection chain. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("acc_completion_satisfaction_bundle")
public class CompletionSatisfactionBundleDO extends BaseBusinessEntity {
    private Long projectId;
    private Long certificateId;
    private Long certificateVersion;
    private String collectionKey;
    private String certificateSnapshot;
}
