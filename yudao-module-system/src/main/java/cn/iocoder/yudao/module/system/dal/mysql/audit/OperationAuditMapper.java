package cn.iocoder.yudao.module.system.dal.mysql.audit;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.audit.OperationAuditDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationAuditMapper extends BaseMapperX<OperationAuditDO> {
}
