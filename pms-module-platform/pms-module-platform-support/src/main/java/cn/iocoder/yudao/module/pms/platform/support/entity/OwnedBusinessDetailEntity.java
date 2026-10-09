package cn.iocoder.yudao.module.pms.platform.support.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Getter;
import lombok.Setter;

/** Parent identity is server-owned; a revision has its own immutable collection. */
@Getter @Setter
public abstract class OwnedBusinessDetailEntity extends BaseBusinessDetailEntity {
    @JsonIgnore private String aggregateType;
    @JsonIgnore private Long aggregateId;
    @JsonIgnore private Long aggregateRevisionId;
    @JsonIgnore private Integer detailOrder;
}
