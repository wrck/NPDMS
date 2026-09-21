package cn.iocoder.yudao.module.pms.platform.dal.mysql.collection;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.collection.CollectionTemplateDO;
import cn.iocoder.yudao.module.pms.platform.dal.mysql.collection.query.CollectionTemplateQuery;
import org.apache.ibatis.annotations.*;
import java.util.List;
@Mapper public interface CollectionTemplateMapper extends BaseMapperX<CollectionTemplateDO> {
    default List<CollectionTemplateDO> list(CollectionTemplateQuery q) {
        return selectList(new LambdaQueryWrapperX<CollectionTemplateDO>().eq(CollectionTemplateDO::getTenantId,q.tenantId())
            .eqIfPresent(CollectionTemplateDO::getPurpose,q.purpose()).eqIfPresent(CollectionTemplateDO::getProtocol,q.protocol())
            .eq(q.publishedOnly(),CollectionTemplateDO::getStatus,"PUBLISHED").orderByDesc(CollectionTemplateDO::getId));
    }
    CollectionTemplateDO lockById(@Param("tenantId") Long tenantId,@Param("id") Long id);
}
