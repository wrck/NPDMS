package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

/** 受控关系声明：同一业务实体可被多个节点/对象使用，不伪造归属。 */
public record BusinessRelationDescriptor(
        String code,
        String name,
        String targetOwnerModule,
        String targetEntityType,
        Cardinality cardinality,
        boolean ownerSide,
        String targetJoinFieldCode) {

    public enum Cardinality { TO_ONE, TO_MANY }
}
