package cn.iocoder.yudao.module.pms.project.dal.dataobject.businessresult;

import lombok.Data;

/** Sequence is a recovery order, not a business revision or a source row ID. */
@Data
public class BusinessResultChannelDO {
    private Long id;
    private Long tenantId;
    private Long projectId;
    private String ownerContext;
    private String entityType;
    private String resultType;
    private Long committedSequence;
}
