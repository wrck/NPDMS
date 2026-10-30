package cn.iocoder.yudao.module.pms.project.api.stageplan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.PaymentAcceptanceTarget;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.PaymentAcceptanceMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.PaymentAcceptanceQuery;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.query.PaymentAcceptanceUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjectPaymentAcceptanceApiImpl implements ProjectPaymentAcceptanceApi {
    private final PaymentAcceptanceMapper mapper;
    private record Candidate(Entry entry, PaymentAcceptanceTarget target, String issue) {}

    @Override
    @Transactional(readOnly = true)
    public List<Result> preview(Request request) { return execute(request, false); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Result> refresh(Request request) { return execute(request, true); }

    private List<Result> execute(Request request, boolean apply) {
        if (request == null || !Objects.equals(request.tenantId(), TenantContextHolder.getRequiredTenantId())
                || request.sourceOwner() == null || !request.sourceOwner().matches("integration:" + request.tenantId() + ":[0-9]+")
                || request.entries() == null) throw new IllegalArgumentException("PAYMENT_ACCEPTANCE_SCOPE_INVALID");
        var candidates = new ArrayList<Candidate>();
        var targets = new HashMap<String, Integer>();
        var sources = new HashMap<String, Integer>();
        for (var entry : request.entries()) {
            if (entry == null || blank(entry.sourceKey()) || entry.sourceKey().length() > 128
                    || blank(entry.contractNo()) || blank(entry.referenceEvent()) || blank(entry.nodeCode())
                    || !Set.of("STAGE", "TASK").contains(Objects.toString(entry.nodeType(), "")) || entry.acceptanceTime() == null) {
                candidates.add(new Candidate(entry, null, "来源字段或显式节点映射缺失")); continue;
            }
            sources.merge(entry.sourceKey(), 1, Integer::sum);
            var found = mapper.selectTargets(new PaymentAcceptanceQuery(request.tenantId(), entry.contractNo(), entry.nodeType(), entry.nodeCode(), apply));
            if (found.size() != 1) {
                candidates.add(new Candidate(entry, null, found.isEmpty() ? "合同及节点编码未命中当前有效项目" : "合同及节点编码命中不唯一")); continue;
            }
            var target = found.getFirst();
            targets.merge(entry.nodeType() + ":" + target.getId(), 1, Integer::sum);
            String issue = null;
            if (entry.boundTargetId() != null && !entry.boundTargetId().equals(target.getBindingId())) issue = "来源原绑定目标已变化，禁止自动改绑";
            else if (target.getSourceOwner() != null && (!target.getSourceOwner().equals(request.sourceOwner())
                    || !Objects.equals(target.getSourceKey(), entry.sourceKey()))) issue = "节点已绑定其他回款来源";
            else if (target.getSourceOwner() == null && target.getAcceptanceTime() != null && !request.adoptExisting())
                issue = "节点已有计划验收时间，请先预览并明确接管已有数据";
            candidates.add(new Candidate(entry, target, issue));
        }
        var results = new ArrayList<Result>();
        for (var candidate : candidates) {
            var entry = candidate.entry(); var target = candidate.target(); String issue = candidate.issue();
            if (entry != null && sources.getOrDefault(entry.sourceKey(), 0) > 1) issue = "来源键重复";
            if (target != null && targets.get(entry.nodeType() + ":" + target.getId()) > 1) issue = "多个回款节点映射到同一目标，未更新";
            if (issue != null) {
                results.add(new Result(entry == null ? "" : entry.sourceKey(), null, "ISSUE",
                        target == null ? null : target.getAcceptanceTime(), entry == null ? null : entry.acceptanceTime(), issue));
                continue;
            }
            boolean changed = !Objects.equals(target.getAcceptanceTime(), entry.acceptanceTime());
            if (apply) {
                if (target.getSourceOwner() == null) {
                    target.setBindingId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getId());
                    mapper.bind(new PaymentAcceptanceMapper.Binding(target.getBindingId(),
                            request.tenantId(), entry.nodeType(), target.getId(), request.sourceOwner(), entry.sourceKey()));
                }
                if (changed && mapper.updateAcceptance(new PaymentAcceptanceUpdate(request.tenantId(), target.getProjectId(), target.getId(),
                        entry.nodeType(), target.getVersion(), entry.acceptanceTime())) != 1)
                    throw new IllegalStateException("PAYMENT_ACCEPTANCE_VERSION_CONFLICT");
            }
            results.add(new Result(entry.sourceKey(), target.getBindingId(), changed ? "UPDATED" : "UNCHANGED", target.getAcceptanceTime(),
                    entry.acceptanceTime(), changed ? "刷新计划验收时间；生效施工计划快照保持不变" : "计划验收时间未变化"));
        }
        return List.copyOf(results);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
