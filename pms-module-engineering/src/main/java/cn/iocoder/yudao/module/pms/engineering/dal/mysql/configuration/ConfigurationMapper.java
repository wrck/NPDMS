package cn.iocoder.yudao.module.pms.engineering.dal.mysql.configuration;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.engineering.controller.admin.configuration.vo.ConfigurationPageReqVO;
import cn.iocoder.yudao.module.pms.engineering.dal.dataobject.configuration.ConfigurationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * PMS 配置调试 Mapper（FR-ENG-023）。
 */
@Mapper
public interface ConfigurationMapper extends BaseMapperX<ConfigurationDO> {

    default PageResult<ConfigurationDO> selectPage(ConfigurationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ConfigurationDO>()
                .eqIfPresent(ConfigurationDO::getProjectId, reqVO.getProjectId())
                .likeIfPresent(ConfigurationDO::getCode, reqVO.getCode())
                .eqIfPresent(ConfigurationDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ConfigurationDO::getEquipmentId, reqVO.getEquipmentId())
                .eqIfPresent(ConfigurationDO::getDebuggerUserId, reqVO.getDebuggerUserId())
                .orderByDesc(ConfigurationDO::getId));
    }

    default ConfigurationDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(ConfigurationDO::getProjectId, projectId, ConfigurationDO::getCode, code);
    }
}
