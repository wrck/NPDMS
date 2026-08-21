package cn.iocoder.yudao.module.pms.project.dal.mysql.platform;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.platform.PlatformIdempotencyRecordDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;

@Mapper
public interface PlatformIdempotencyRecordMapper extends BaseMapperX<PlatformIdempotencyRecordDO> {

    @Insert("""
            INSERT IGNORE INTO plt_idempotency_record
                (tenant_id, scope_code, actor_id, idempotency_key, request_digest, status, version)
            VALUES
                (#{tenantId}, #{scopeCode}, #{actorId}, #{idempotencyKey}, #{requestDigest}, #{status}, 0)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertIfAbsent(PlatformIdempotencyRecordDO record);

    default PlatformIdempotencyRecordDO selectByScope(Long tenantId, String scopeCode,
                                                       Long actorId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<PlatformIdempotencyRecordDO>()
                .eq(PlatformIdempotencyRecordDO::getTenantId, tenantId)
                .eq(PlatformIdempotencyRecordDO::getScopeCode, scopeCode)
                .eq(PlatformIdempotencyRecordDO::getActorId, actorId)
                .eq(PlatformIdempotencyRecordDO::getIdempotencyKey, idempotencyKey));
    }
}
