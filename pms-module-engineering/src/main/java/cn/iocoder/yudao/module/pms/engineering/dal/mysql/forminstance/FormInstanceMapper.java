package cn.iocoder.yudao.module.pms.engineering.dal.mysql.forminstance;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.forminstance.vo.FormInstancePageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.forminstance.FormInstanceDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FormInstanceMapper extends BaseMapperX<FormInstanceDO> {

    default PageResult<FormInstanceDO> selectPage(FormInstancePageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<FormInstanceDO>()
                .eqIfPresent(FormInstanceDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(FormInstanceDO::getTemplateId, reqVO.getTemplateId())
                .likeIfPresent(FormInstanceDO::getCode, reqVO.getCode())
                .likeIfPresent(FormInstanceDO::getName, reqVO.getName())
                .eqIfPresent(FormInstanceDO::getStatus, reqVO.getStatus())
                .eqIfPresent(FormInstanceDO::getFillerUserId, reqVO.getFillerUserId())
                .eqIfPresent(FormInstanceDO::getApproverUserId, reqVO.getApproverUserId())
                .betweenIfPresent(FormInstanceDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(FormInstanceDO::getId));
    }

    /**
     * 按编号查询，用于全局唯一性校验
     */
    default FormInstanceDO selectByCode(String code) {
        return selectOne(FormInstanceDO::getCode, code);
    }

    /**
     * 按模板ID查询实例数量（用于模板停用前校验）
     */
    default Long selectCountByTemplateId(Long templateId) {
        return selectCount(FormInstanceDO::getTemplateId, templateId);
    }

    /**
     * 按项目ID查询实例数量
     */
    default Long selectCountByProjectId(Long projectId) {
        return selectCount(FormInstanceDO::getProjectId, projectId);
    }

}
