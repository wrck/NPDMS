package cn.iocoder.yudao.module.pms.cutover.dal.mysql.closure;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.api.result.CutoverResultApi;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.closure.CutoverClosureDO;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CutoverSuccessMapper extends BaseMapperX<CutoverClosureDO> {
    default List<CutoverClosureDO> projectSuccesses(CutoverResultApi.ProjectQuery query) {
        return selectList(new LambdaQueryWrapperX<CutoverClosureDO>()
                .eq(CutoverClosureDO::getTenantId, query.tenantId()).eq(CutoverClosureDO::getProjectId, query.projectId())
                .eq(CutoverClosureDO::getStatusCode, "SUBMITTED").eq(CutoverClosureDO::getFinalResultCode, "SUCCESS")
                .orderByAsc(CutoverClosureDO::getId));
    }
}
