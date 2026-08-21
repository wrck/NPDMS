package cn.iocoder.yudao.module.system.dal.mysql.idempotency;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.system.dal.dataobject.idempotency.IdempotencyRecordDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IdempotencyRecordMapper extends BaseMapperX<IdempotencyRecordDO> {

    @Insert("""
            INSERT IGNORE INTO plt_idempotency_record
                (id, tenant_id, scope_code, actor_id, idempotency_key, request_sha256, status, version)
            VALUES
                (#{id}, #{tenantId}, #{scopeCode}, #{actorId}, #{idempotencyKey}, #{requestSha256}, #{status}, 0)
            """)
    int insertIgnore(IdempotencyRecordDO record);

    default IdempotencyRecordDO selectForUpdate(long tenantId, long actorId, String scopeCode,
                                                 String idempotencyKey) {
        return selectOne(new LambdaQueryWrapper<IdempotencyRecordDO>()
                .eq(IdempotencyRecordDO::getTenantId, tenantId)
                .eq(IdempotencyRecordDO::getActorId, actorId)
                .eq(IdempotencyRecordDO::getScopeCode, scopeCode)
                .eq(IdempotencyRecordDO::getIdempotencyKey, idempotencyKey)
                .last("FOR UPDATE"));
    }

    @Update("""
            UPDATE plt_idempotency_record
            SET status = 'COMPLETED', resource_id = #{resourceId}, response_json = #{responseJson},
                version = version + 1, update_time = CURRENT_TIMESTAMP
            WHERE id = #{recordId} AND status = 'RESERVED'
            """)
    int complete(long recordId, long resourceId, String responseJson);
}
