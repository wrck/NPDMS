package cn.iocoder.yudao.module.pms.commerce.service.migration;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.commerce.api.migration.ContractCollectionPlanMigrationApi;
import cn.iocoder.yudao.module.pms.commerce.dal.dataobject.associated.ContractCollectionPlanDO;
import cn.iocoder.yudao.module.pms.commerce.dal.mysql.migration.ContractCollectionPlanMapper;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Complete formal carry; it does not complete acceptance, receipts, or project workflow. */
@Service
@RequiredArgsConstructor
public class ContractCollectionPlanMigrationService implements ContractCollectionPlanMigrationApi {
    private final ContractCollectionPlanMapper mapper;
    private final AdminUserApi users;

    @Override
    @Transactional(readOnly = true)
    public List<Result> preview(Request request) { return execute(request, false); }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<Result> apply(Request request) { return execute(request, true); }

    private List<Result> execute(Request request, boolean apply) {
        long tenant = TenantContextHolder.getRequiredTenantId();
        if (request == null || !Objects.equals(request.tenantId(), tenant) || request.actorId() == null
                || request.actorId() <= 0 || request.owner() == null
                || !request.owner().matches("integration:" + tenant + ":[0-9]+") || request.entries() == null)
            throw new IllegalArgumentException("COLLECTION_PLAN_SCOPE_INVALID");
        users.validateUser(request.actorId());
        Set<String> keys = new HashSet<>();
        Set<Long> targets = new HashSet<>();
        for (var entry : request.entries()) {
            if (entry == null || entry.sourceKey() == null || entry.sourceKey().isEmpty()
                    || !keys.add(entry.sourceKey())) throw new IllegalArgumentException("COLLECTION_PLAN_SOURCE_DUPLICATE_OR_INVALID");
            Long id = target(entry, request.adoptExisting());
            if (id != null && !targets.add(id)) throw new IllegalArgumentException("COLLECTION_PLAN_TARGET_DUPLICATE");
        }
        List<Result> result = new ArrayList<>();
        for (var entry : request.entries()) {
            Long id = target(entry, request.adoptExisting());
            var identity = new ContractCollectionPlanMapper.IdentityQuery(tenant,id);
            var row = id == null ? null : apply ? mapper.selectIdentityForUpdate(identity) : mapper.selectIdentity(identity);
            Plan before = row == null ? null : snapshot(row);
            if (id != null && (before == null || !Objects.equals(before.tenantId(), tenant) || Boolean.TRUE.equals(before.deleted()))) {
                result.add(issue(entry, before, "正式计划不存在、跨租户或已删除；禁止替换或复活")); continue;
            }
            Plan incoming = entry.incoming();
            if (!valid(incoming, tenant)) {
                result.add(issue(entry, before, "原业务字段、历史日期或类型不完整，不能截断或编造")); continue;
            }
            if (incoming.id() != null && !Objects.equals(incoming.id(), id)) {
                result.add(issue(entry, before, "输入正式主键与绑定目标不同")); continue;
            }
            if (entry.lastApplied() != null && (!Objects.equals(entry.lastApplied().id(), id)
                    || !Objects.equals(entry.lastApplied().tenantId(), tenant) || !Objects.equals(before, entry.lastApplied()))) {
                result.add(issue(entry, before, "正式计划已偏离上次同步结果；禁止覆盖当前业务修改")); continue;
            }
            // Until a stable upstream identity is confirmed, baseline imports may not interpret a reused ID as an update.
            if (before != null && !sameBusiness(before, incoming)) {
                result.add(issue(entry, before, "来源业务内容与绑定计划不同；稳定来源身份尚未裁决，禁止自动重绑或覆盖")); continue;
            }
            if (before != null && entry.lastApplied() == null && !request.adoptExisting()) {
                result.add(issue(entry, before, "既有计划尚未批准接管")); continue;
            }
            var contracts = incoming.contractNo() == null ? List.<Long>of()
                    : mapper.selectExactContractIds(new ContractCollectionPlanMapper.ContractQuery(tenant, incoming.contractNo()));
            Long contractId = before == null ? null : before.contractId();
            if (contractId != null && !contracts.contains(contractId)) {
                result.add(issue(entry, before, "既有正式合同引用与原合同号不一致")); continue;
            }
            if (incoming.contractId() != null && !contracts.contains(incoming.contractId())) {
                result.add(issue(entry, before, "输入合同引用无法按原合同号精确核实")); continue;
            }
            if (contractId == null) contractId = contracts.size() == 1 ? contracts.getFirst() : null;
            if (incoming.contractId() != null && !Objects.equals(incoming.contractId(), contractId)) {
                result.add(issue(entry, before, "输入合同引用不能唯一关联")); continue;
            }
            Plan after = canonical(incoming, id, tenant, contractId);
            String action = before == null ? "CREATED" : Objects.equals(before, after) ? "UNCHANGED" : "UPDATED";
            if (apply && !"UNCHANGED".equals(action)) {
                if (before == null) after = canonical(after, IdWorker.getId(), tenant, contractId);
                var saved = entity(after);
                int changed = before == null ? mapper.insertPlan(saved) : mapper.updatePlan(saved);
                if (changed != 1) throw new IllegalStateException("COLLECTION_PLAN_WRITE_CONFLICT");
            }
            result.add(new Result(entry.sourceKey(), action, before, after,
                    contractId == null ? "完整计划已承接；合同未唯一关联，保持待关联" : "完整合同回款计划；不生成到账或验收完成事实"));
        }
        return List.copyOf(result);
    }

    private static Long target(Entry entry, boolean adopt) {
        if (entry.adjudicatedExistingId() != null && (!adopt || entry.adjudicatedExistingId() <= 0
                || entry.targetId() != null && !entry.targetId().equals(entry.adjudicatedExistingId())))
            throw new IllegalArgumentException("COLLECTION_PLAN_ADOPTION_INVALID");
        if (entry.targetId() != null && entry.targetId() <= 0) throw new IllegalArgumentException("COLLECTION_PLAN_TARGET_INVALID");
        return entry.targetId() == null ? entry.adjudicatedExistingId() : entry.targetId();
    }
    private static boolean valid(Plan plan, long tenant) {
        return plan != null && Objects.equals(plan.tenantId(), tenant) && Boolean.FALSE.equals(plan.deleted())
                && plan.createTime() != null && plan.updateTime() != null
                && length(plan.contractNo(),64) && length(plan.batchCode(),32) && length(plan.collectionItemName(),64)
                && length(plan.referenceEventName(),64) && length(plan.marketingFeedback(),2000) && length(plan.dataSource(),25)
                && length(plan.creator(),64) && length(plan.updater(),64);
    }
    private static boolean length(String value, int maximum) { return value == null || value.codePointCount(0,value.length()) <= maximum; }
    private static Result issue(Entry entry, Plan before, String message) { return new Result(entry.sourceKey(),"ISSUE",before,null,message); }
    private static boolean sameBusiness(Plan before, Plan incoming) {
        return Objects.equals(business(before), business(incoming));
    }
    private static Plan business(Plan value) {
        return new Plan(null,value.tenantId(),null,value.contractNo(),value.batchCode(),value.collectionItemName(),value.referenceEventName(),
                value.eventPlannedAt(),value.afterDays(),value.eventActualFinishedAt(),value.marketingFeedback(),value.dataSource(),
                value.effectiveFrom(),value.effectiveTo(),null,null,null,null,value.deleted());
    }
    private static Plan canonical(Plan value, Long id, long tenant, Long contractId) {
        return new Plan(id,tenant,contractId,value.contractNo(),value.batchCode(),value.collectionItemName(),value.referenceEventName(),
                value.eventPlannedAt(),value.afterDays(),value.eventActualFinishedAt(),value.marketingFeedback(),value.dataSource(),
                value.effectiveFrom(),value.effectiveTo(),value.creator(),value.createTime(),value.updater(),value.updateTime(),false);
    }
    private static Plan snapshot(ContractCollectionPlanDO value) {
        return new Plan(value.getId(),value.getTenantId(),value.getContractId(),value.getContractNo(),value.getBatchCode(),
                value.getCollectionItemName(),value.getReferenceEventName(),value.getEventPlannedAt(),value.getAfterDays(),
                value.getEventActualFinishedAt(),value.getMarketingFeedback(),value.getDataSource(),value.getEffectiveFrom(),
                value.getEffectiveTo(),value.getCreator(),value.getCreateTime(),value.getUpdater(),value.getUpdateTime(),value.getDeleted());
    }
    private static ContractCollectionPlanDO entity(Plan value) {
        var row = JsonUtils.parseObject(JsonUtils.toJsonString(value),ContractCollectionPlanDO.class);
        // Explicit assignment avoids serializer settings losing milliseconds in history.
        row.setEventPlannedAt(value.eventPlannedAt()); row.setEventActualFinishedAt(value.eventActualFinishedAt());
        row.setEffectiveFrom(value.effectiveFrom()); row.setEffectiveTo(value.effectiveTo());
        row.setCreateTime(value.createTime()); row.setUpdateTime(value.updateTime());
        return row;
    }
}
