package cn.iocoder.yudao.module.pms.project.service.projectmanual;

import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.IdempotencyRecordDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.IdempotencyRecordMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * API 命令幂等记录 Service 实现（F-PM01，薄存取；tenant_id 由租户插件过滤，显式条件兜底）
 */
@Service
public class IdempotencyRecordServiceImpl implements IdempotencyRecordService {

    @Resource
    private IdempotencyRecordMapper idempotencyRecordMapper;

    @Override
    public IdempotencyRecordDO findByKey(Long tenantId, String command, Long actorId, String idempotencyKey) {
        return idempotencyRecordMapper.selectOne(new LambdaQueryWrapperX<IdempotencyRecordDO>()
                .eq(IdempotencyRecordDO::getTenantId, tenantId)
                .eq(IdempotencyRecordDO::getCommand, command)
                .eq(IdempotencyRecordDO::getActorId, actorId)
                .eq(IdempotencyRecordDO::getIdempotencyKey, idempotencyKey));
    }

    @Override
    public void save(IdempotencyRecordDO record) {
        idempotencyRecordMapper.insert(record);
    }
}
