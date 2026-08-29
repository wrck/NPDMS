package cn.iocoder.yudao.module.pms.commerce.api.scope;

import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeAcceptanceLockCommand;
import cn.iocoder.yudao.module.pms.commerce.api.scope.dto.DeliveryScopeVersionFact;

import java.util.List;

/** ACC在项目行已锁定的同一事务中读取并锁定COM当前范围版本。 */
public interface DeliveryScopeAcceptanceLockApi {

    List<DeliveryScopeVersionFact> lockCurrentByProject(DeliveryScopeAcceptanceLockCommand command);
}
