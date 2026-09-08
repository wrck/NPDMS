package cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.deliveryconfiguration.DeliveryDefinitionRevisionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.query.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
/** PM-03: locking SQL is confined to XML. */
@Mapper
public interface DeliveryDefinitionRevisionMapper extends BaseMapperX<DeliveryDefinitionRevisionDO> {
    default PageResult<DeliveryDefinitionRevisionDO> selectPage(DeliveryDefinitionPageQuery query) {
        return selectPage(query, new LambdaQueryWrapperX<DeliveryDefinitionRevisionDO>()
                .eq(DeliveryDefinitionRevisionDO::getTenantId, query.getTenantId())
                .eqIfPresent(DeliveryDefinitionRevisionDO::getDefinitionKind, query.getDefinitionKind())
                .eqIfPresent(DeliveryDefinitionRevisionDO::getDefinitionCode, query.getDefinitionCode())
                .eqIfPresent(DeliveryDefinitionRevisionDO::getRevisionState, query.getRevisionState())
                .orderByAsc(DeliveryDefinitionRevisionDO::getDefinitionKind, DeliveryDefinitionRevisionDO::getDefinitionCode)
                .orderByDesc(DeliveryDefinitionRevisionDO::getRevisionNo, DeliveryDefinitionRevisionDO::getId));
    }
    List<DeliveryDefinitionRevisionDO> lockIdentity(@Param("query") DeliveryDefinitionIdentityQuery query);
    DeliveryDefinitionRevisionDO lockRevision(@Param("query") DeliveryDefinitionByIdQuery query);
    int replaceDraft(@Param("row") DeliveryDefinitionRevisionDO row);
    int publish(@Param("row") DeliveryDefinitionRevisionDO row);
    int disable(@Param("row") DeliveryDefinitionRevisionDO row);
}
