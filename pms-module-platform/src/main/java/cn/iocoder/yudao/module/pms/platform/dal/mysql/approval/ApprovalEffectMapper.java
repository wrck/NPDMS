package cn.iocoder.yudao.module.pms.platform.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalEffectDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Optional;

@Mapper
public interface ApprovalEffectMapper extends BaseMapperX<ApprovalEffectDO> {

    default Optional<ApprovalEffectDO> selectByKey(Long attemptRowId, String idempotencyKey) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<ApprovalEffectDO>()
                .eq(ApprovalEffectDO::getAttemptRowId, attemptRowId)
                .eq(ApprovalEffectDO::getIdempotencyKey, idempotencyKey)));
    }

    default List<ApprovalEffectDO> selectByAttemptRow(Long attemptRowId) {
        return selectList(new LambdaQueryWrapperX<ApprovalEffectDO>()
                .eq(ApprovalEffectDO::getAttemptRowId, attemptRowId)
                .orderByAsc(ApprovalEffectDO::getId));
    }
}
