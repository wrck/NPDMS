package cn.iocoder.yudao.module.pms.platform.dal.mysql.approval;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.approval.ApprovalOpinionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ApprovalOpinionMapper extends BaseMapperX<ApprovalOpinionDO> {

    default List<ApprovalOpinionDO> selectByAttemptRow(Long attemptRowId) {
        return selectList(new LambdaQueryWrapperX<ApprovalOpinionDO>()
                .eq(ApprovalOpinionDO::getAttemptRowId, attemptRowId)
                .orderByAsc(ApprovalOpinionDO::getId));
    }
}
