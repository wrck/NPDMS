package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness;

import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessQuery;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessResult;

/** 显式重算 EXE-06 并保存不可变快照；只供授权的项目入口调用。 */
public interface ImplementationReadinessEvaluationApi {
    ImplementationReadinessResult evaluate(ImplementationReadinessQuery query);
}
