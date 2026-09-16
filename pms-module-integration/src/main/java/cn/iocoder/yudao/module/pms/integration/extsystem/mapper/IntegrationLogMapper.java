package cn.iocoder.yudao.module.pms.integration.extsystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.iocoder.yudao.module.pms.integration.extsystem.entity.IntegrationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for {@link IntegrationLog}.
 */
@Mapper
public interface IntegrationLogMapper extends BaseMapper<IntegrationLog> {
}
