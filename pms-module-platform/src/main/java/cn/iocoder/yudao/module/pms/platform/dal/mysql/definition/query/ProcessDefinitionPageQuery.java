package cn.iocoder.yudao.module.pms.platform.dal.mysql.definition.query;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.platform.dal.dataobject.definition.ProcessDefinitionDO;
import lombok.Getter;
import lombok.Setter;

/** 定义分页查询：场景化条件，空条件不扩大租户边界（租户由框架注入）。 */
@Getter
@Setter
public class ProcessDefinitionPageQuery extends PageParam {

    private String definitionCode;
    private String ownerModule;
    private String entityType;
    private String status;

    public LambdaQueryWrapperX<ProcessDefinitionDO> toWrapper() {
        return new LambdaQueryWrapperX<ProcessDefinitionDO>()
                .eqIfPresent(ProcessDefinitionDO::getDefinitionCode, definitionCode)
                .eqIfPresent(ProcessDefinitionDO::getOwnerModule, ownerModule)
                .eqIfPresent(ProcessDefinitionDO::getEntityType, entityType)
                .eqIfPresent(ProcessDefinitionDO::getStatus, status)
                .orderByDesc(ProcessDefinitionDO::getId);
    }
}
