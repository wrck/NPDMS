package cn.iocoder.yudao.module.pms.platform.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalAttemptDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ApprovalAttemptMapper extends BaseMapperX<ApprovalAttemptDO> {

    /** 同主体同用途同尝试键唯一；批次内各主体行共用 attempt_id。 */
    default Optional<ApprovalAttemptDO> selectBySubjectAndAttempt(String ownerModule, String entityType,
                                                                  Long entityId, String purpose,
                                                                  String attemptId) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<ApprovalAttemptDO>()
                .eq(ApprovalAttemptDO::getOwnerModule, ownerModule)
                .eq(ApprovalAttemptDO::getEntityType, entityType)
                .eq(ApprovalAttemptDO::getEntityId, entityId)
                .eq(ApprovalAttemptDO::getPurpose, purpose)
                .eq(ApprovalAttemptDO::getAttemptId, attemptId)));
    }

    default List<ApprovalAttemptDO> selectByInstanceRef(String instanceRef) {
        return selectList(new LambdaQueryWrapperX<ApprovalAttemptDO>()
                .eq(ApprovalAttemptDO::getInstanceRef, instanceRef)
                .orderByAsc(ApprovalAttemptDO::getId));
    }

    default List<ApprovalAttemptDO> selectBySubject(String ownerModule, String entityType, Long entityId,
                                                    String purpose) {
        return selectList(new LambdaQueryWrapperX<ApprovalAttemptDO>()
                .eq(ApprovalAttemptDO::getOwnerModule, ownerModule)
                .eq(ApprovalAttemptDO::getEntityType, entityType)
                .eq(ApprovalAttemptDO::getEntityId, entityId)
                .eq(purpose != null && !purpose.isBlank(), ApprovalAttemptDO::getPurpose, purpose)
                .orderByDesc(ApprovalAttemptDO::getId));
    }

    default List<ApprovalAttemptDO> selectByPrevious(Long previousAttemptId) {
        return selectList(new LambdaQueryWrapperX<ApprovalAttemptDO>()
                .eq(ApprovalAttemptDO::getPreviousAttemptId, previousAttemptId));
    }
}
