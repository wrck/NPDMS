package cn.iocoder.yudao.module.pms.platform.api.businessmodel.model;

/**
 * 业务域对统一目录的一次声明：模型描述 + 正常持久化映射（MyBatis-Plus Mapper）。
 * 修订 Mapper 可选；新增实体只在本域提交声明，不在框架、模板或前端登记名称。
 */
public record BusinessModelDeclaration(
        BusinessModelDescriptor descriptor,
        Class<?> entityClass,
        Object mapper,
        Object revisionMapper,
        String nativeEntityType) {
    public BusinessModelDeclaration(BusinessModelDescriptor descriptor,Class<?> entityClass,Object mapper,Object revisionMapper) {
        this(descriptor,entityClass,mapper,revisionMapper,null);
    }
}
