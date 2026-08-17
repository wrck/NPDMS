package cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.IdempotencyRecordDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * API 命令幂等记录 Mapper（F-PM01 / V57；幂等拦截在 T4 Controller 层）
 */
@Mapper
public interface IdempotencyRecordMapper extends BaseMapperX<IdempotencyRecordDO> {
}
