package cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverApprovalResultReferenceDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.query.CutoverApprovalResultReferenceListQuery;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

/** 审批冻结结果集合：固定表与固定父引用，保留租户及空集合语义。 */
@Mapper
public interface CutoverApprovalResultReferenceMapper extends BaseMapperX<CutoverApprovalResultReferenceDO> {

    default List<CutoverApprovalResultReferenceDO> selectListByParents(CutoverApprovalResultReferenceListQuery query) {
        if (query == null || query.tenantId() == null || query.approvalInstanceIds() == null || query.approvalInstanceIds().isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<CutoverApprovalResultReferenceDO>()
                .eq(CutoverApprovalResultReferenceDO::getTenantId, query.tenantId())
                .in(CutoverApprovalResultReferenceDO::getApprovalInstanceId, query.approvalInstanceIds())
                .orderByAsc(CutoverApprovalResultReferenceDO::getSortOrder)
                .orderByAsc(CutoverApprovalResultReferenceDO::getId));
    }
}
