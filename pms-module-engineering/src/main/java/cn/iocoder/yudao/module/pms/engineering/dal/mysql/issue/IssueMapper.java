package cn.iocoder.yudao.module.pms.engineering.dal.mysql.issue;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.issue.vo.IssuePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.issue.IssueDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface IssueMapper extends BaseMapperX<IssueDO> {

    default PageResult<IssueDO> selectPage(IssuePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<IssueDO>()
                .eqIfPresent(IssueDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(IssueDO::getCode, reqVO.getCode())
                .likeIfPresent(IssueDO::getName, reqVO.getName())
                .eqIfPresent(IssueDO::getSource, reqVO.getSource())
                .eqIfPresent(IssueDO::getSeverity, reqVO.getSeverity())
                .eqIfPresent(IssueDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(IssueDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(IssueDO::getDeadline, reqVO.getDeadline())
                .orderByDesc(IssueDO::getId));
    }

    default IssueDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(IssueDO::getProjectId, projectId, IssueDO::getCode, code);
    }

    default Long selectOpenCountByProjectId(Long projectId) {
        return selectCount(new LambdaQueryWrapperX<IssueDO>()
                .eq(IssueDO::getProjectId, projectId)
                .in(IssueDO::getStatus, 0, 1, 2, 4));
    }

    /**
     * 查询项目下未关闭（非 3已关闭）的问题列表，用于验收门禁校验。
     */
    default List<IssueDO> selectUnclosedByProject(Long projectId) {
        return selectList(new LambdaQueryWrapperX<IssueDO>()
                .eq(IssueDO::getProjectId, projectId)
                .ne(IssueDO::getStatus, 3));
    }

}
