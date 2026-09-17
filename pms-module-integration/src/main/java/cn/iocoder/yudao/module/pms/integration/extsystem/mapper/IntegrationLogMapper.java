package cn.iocoder.yudao.module.pms.integration.extsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for {@link IntegrationLog}.
 */
@Mapper
public interface IntegrationLogMapper extends BaseMapper<IntegrationLog> {

    /** Outcome nulls explicitly clear obsolete failure/response state, without changing global field strategies. */
    default int updateDeliveryOutcome(IntegrationLog outcome) {
        var update = new LambdaUpdateWrapper<IntegrationLog>().eq(IntegrationLog::getId, outcome.getId());
        if (outcome.getErrorMessage() == null) update.set(IntegrationLog::getErrorMessage, null);
        if (outcome.getNextRetryTime() == null) update.set(IntegrationLog::getNextRetryTime, null);
        if (outcome.getResponseBody() == null) update.set(IntegrationLog::getResponseBody, null);
        return update(outcome, update);
    }
}
