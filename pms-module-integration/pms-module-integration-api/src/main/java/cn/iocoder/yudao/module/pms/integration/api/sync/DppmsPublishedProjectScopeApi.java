package cn.iocoder.yudao.module.pms.integration.api.sync;

import java.util.List;

/** Current complete P16 publication. Historical bindings and project authorization are separate. */
public interface DppmsPublishedProjectScopeApi {
    record Query(Long tenantId, Long sourceConnectionId, List<String> sourceProjectKeys) {}
    record Scope(Long runId, List<DppmsProjectBindingApi.ProjectBinding> projects) {}
    /** Fails closed before the first successful complete publication; an empty selection stays empty. */
    Scope resolve(Query query);
}
