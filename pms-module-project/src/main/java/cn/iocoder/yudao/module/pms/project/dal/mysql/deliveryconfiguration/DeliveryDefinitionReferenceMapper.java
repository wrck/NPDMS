package cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliveryconfiguration.DeliveryDefinitionReferenceDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.query.DeliveryDefinitionReferencesQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
/** PM-03: exact outgoing reference slots, draft-only replacement. */
@Mapper
public interface DeliveryDefinitionReferenceMapper extends BaseMapperX<DeliveryDefinitionReferenceDO> {
    default List<DeliveryDefinitionReferenceDO> selectReferences(DeliveryDefinitionReferencesQuery query) {
        return selectList(new LambdaQueryWrapperX<DeliveryDefinitionReferenceDO>()
                .eq(DeliveryDefinitionReferenceDO::getTenantId, query.tenantId())
                .eq(DeliveryDefinitionReferenceDO::getOwnerRevisionId, query.ownerRevisionId())
                .orderByAsc(DeliveryDefinitionReferenceDO::getReferenceKey));
    }
    int deleteDraftReferences(@Param("query") DeliveryDefinitionReferencesQuery query);
}
