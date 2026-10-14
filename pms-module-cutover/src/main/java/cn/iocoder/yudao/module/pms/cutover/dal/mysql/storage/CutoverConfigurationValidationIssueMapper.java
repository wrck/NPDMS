package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverConfigurationValidationIssueDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverConfigurationValidationIssueListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 配置校验问题明细：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverConfigurationValidationIssueMapper extends BaseMapperX<CutoverConfigurationValidationIssueDO> {

    default List<CutoverConfigurationValidationIssueDO> selectListByParents(CutoverConfigurationValidationIssueListQuery query) {
        if (query == null || query.tenantId() == null || query.configurationRevisionIds() == null || query.configurationRevisionIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverConfigurationValidationIssueDO>()
                .eq(CutoverConfigurationValidationIssueDO::getTenantId, query.tenantId())
                .in(CutoverConfigurationValidationIssueDO::getConfigurationRevisionId, query.configurationRevisionIds())
                .orderByAsc(CutoverConfigurationValidationIssueDO::getIssueNo)
                .orderByAsc(CutoverConfigurationValidationIssueDO::getId));
    }
}
