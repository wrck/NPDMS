package cn.iocoder.yudao.module.pms.asset.dal.mysql.configurationlog;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.pms.asset.dal.dataobject.configurationlog.DeviceDownloadGrantDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;

@Mapper
public interface DeviceDownloadGrantMapper extends BaseMapperX<DeviceDownloadGrantDO> {

    default DeviceDownloadGrantDO selectByTokenDigest(String tokenDigest) {
        return selectOne(new LambdaQueryWrapperX<DeviceDownloadGrantDO>()
                .eq(DeviceDownloadGrantDO::getTokenDigest, tokenDigest));
    }

    default int consume(Long tenantId, String tokenDigest, Long userId, LocalDateTime consumedAt) {
        DeviceDownloadGrantDO update = new DeviceDownloadGrantDO();
        update.setConsumedAt(consumedAt);
        return update(update, new LambdaUpdateWrapper<DeviceDownloadGrantDO>()
                .eq(DeviceDownloadGrantDO::getTenantId, tenantId)
                .eq(DeviceDownloadGrantDO::getTokenDigest, tokenDigest)
                .eq(DeviceDownloadGrantDO::getUserId, userId)
                .isNull(DeviceDownloadGrantDO::getConsumedAt)
                .gt(DeviceDownloadGrantDO::getExpiresAt, consumedAt));
    }
}
