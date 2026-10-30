package cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection;
import cn.iocoder.yudao.module.pms.platform.support.entity.BaseBusinessEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;
import java.time.LocalDateTime;
@TableName("plt_collection_template") @Data @EqualsAndHashCode(callSuper = true)
public class CollectionTemplateDO extends BaseBusinessEntity {
    private String templateCode;
    private String name;
    private String ownerContext;
    private String purpose;
    private String protocol;
    private String deviceModel;
    private Integer revision;
    private String commandText;
    private String contentHash;
    private String status;
    private Boolean publicationStarted;
    private LocalDateTime publishedAt;
}
