package cn.iocoder.yudao.module.pms.asset.api.device;

import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolutionResult;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeResolveQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationQuery;
import cn.iocoder.yudao.module.pms.asset.api.device.dto.DeviceScopeRevalidationResult;

/** AST设备稳定身份与当前直接项目归属的批量只读事实。 */
public interface DeviceScopeFactApi {

    DeviceScopeResolutionResult resolveBySerials(DeviceScopeResolveQuery query);

    DeviceScopeRevalidationResult lockAndRevalidate(DeviceScopeRevalidationQuery query);

}
