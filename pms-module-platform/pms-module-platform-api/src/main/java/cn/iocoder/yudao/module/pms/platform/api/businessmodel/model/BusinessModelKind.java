package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

/** 实体在继承体系中的角色：聚合根、聚合明细或关系实体。明细与关系的写入服从所属聚合。 */
public enum BusinessModelKind {
    AGGREGATE_ROOT, DETAIL, RELATION
}
