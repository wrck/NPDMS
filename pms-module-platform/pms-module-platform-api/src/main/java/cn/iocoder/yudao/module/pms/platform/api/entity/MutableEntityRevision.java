package cn.iocoder.yudao.module.pms.platform.api.entity;

/**
 * 可变修订实体契约：继承式内容历史的通用实现要求修订实体在继承业务字段之外，
 * 以统一命名暴露修订元数据的写入能力。业务 Owner 只需让修订 DO 实现本接口，
 * 无须为每个修订实体手写版本 Provider；专业 Owner 仍可提供自己的 EntityVersionProvider。
 */
public interface MutableEntityRevision extends EntityRevision {

    void setEntityId(Long entityId);

    void setRevisionNo(Integer revisionNo);

    void setSourceRevisionId(Long sourceRevisionId);

    void setBaseEffectiveRevisionId(Long baseEffectiveRevisionId);

    void setBaseEntityVersion(Integer baseEntityVersion);

    void setChangeReason(String changeReason);

    void setFrozenBy(Long frozenBy);

    void setFrozenAt(java.time.LocalDateTime frozenAt);

    void setRevisionState(EntityVersionProvider.Revision.State state);

    void setEffective(boolean effective);

    /** 乐观锁并发依据（列 version）；通用实现按基类契约写入。 */
    void setVersion(Long version);
}
