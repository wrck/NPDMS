package cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness;

import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessQuery;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessResult;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessRevalidationQuery;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessSnapshotQuery;
import cn.iocoder.yudao.module.pms.engineering.api.implementationreadiness.dto.ImplementationReadinessSnapshotFact;

/** EXE-06割接前实施就绪快照的公共只读事实。 */
public interface ImplementationReadinessApi {

    ImplementationReadinessResult inspect(ImplementationReadinessQuery query);

    ImplementationReadinessResult lockAndRevalidate(ImplementationReadinessRevalidationQuery query);

    ImplementationReadinessSnapshotFact readSnapshot(ImplementationReadinessSnapshotQuery query);
}
