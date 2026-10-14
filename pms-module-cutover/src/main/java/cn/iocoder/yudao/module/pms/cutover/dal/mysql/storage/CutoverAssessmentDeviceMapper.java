package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverAssessmentDeviceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverAssessmentDeviceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 评估冻结设备事实：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverAssessmentDeviceMapper extends BaseMapperX<CutoverAssessmentDeviceDO> {

    default List<CutoverAssessmentDeviceDO> selectListByParents(CutoverAssessmentDeviceListQuery query) {
        if (query == null || query.tenantId() == null || query.assessmentIds() == null || query.assessmentIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverAssessmentDeviceDO>()
                .eq(CutoverAssessmentDeviceDO::getTenantId, query.tenantId())
                .in(CutoverAssessmentDeviceDO::getAssessmentId, query.assessmentIds())
                .orderByAsc(CutoverAssessmentDeviceDO::getSortOrder)
                .orderByAsc(CutoverAssessmentDeviceDO::getId));
    }
}
