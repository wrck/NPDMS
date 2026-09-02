package cn.iocoder.yudao.module.pms.project.api.acceptancescope;

import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardQuery;
import cn.iocoder.yudao.module.pms.project.api.acceptancescope.dto.AcceptanceScopeGuardResult;

public interface AcceptanceScopeGuardApi {

    AcceptanceScopeGuardResult checkReduction(AcceptanceScopeGuardQuery query);
}
