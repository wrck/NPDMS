package cn.iocoder.yudao.module.pms.integration.api.sync;

import java.util.List;
import java.util.Map;

/** 分块事务内的目标变更通知；监听方失败必须使同步分块回滚，预览不发布。 */
public record GenericSyncTargetsChanged(Long tenantId, List<Target> targets) {
    public record Target(String table, Map<String,Object> before, Map<String,Object> after) {}
}
