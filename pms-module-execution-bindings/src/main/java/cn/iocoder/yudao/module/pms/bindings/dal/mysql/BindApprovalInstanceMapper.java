package cn.iocoder.yudao.module.pms.bindings.dal.mysql;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.bindings.dal.dataobject.BindApprovalInstanceDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface BindApprovalInstanceMapper extends BaseMapperX<BindApprovalInstanceDO> {

    default Optional<BindApprovalInstanceDO> selectByInstanceRef(String instanceRef) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<BindApprovalInstanceDO>()
                .eq(BindApprovalInstanceDO::getInstanceRef, instanceRef)));
    }

    default Optional<BindApprovalInstanceDO> selectByAttemptHint(String attemptHint) {
        return Optional.ofNullable(selectOne(new LambdaQueryWrapperX<BindApprovalInstanceDO>()
                .eq(BindApprovalInstanceDO::getAttemptHint, attemptHint)));
    }
}
