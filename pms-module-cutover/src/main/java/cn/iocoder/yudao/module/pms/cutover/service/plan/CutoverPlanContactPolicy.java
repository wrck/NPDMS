package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.taskv2.CutoverTaskDO;
import cn.iocoder.yudao.module.pms.customer.api.contact.ProjectContactReferenceApi;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverPlanRevisionDO;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.planv2.CutoverSupportArrangementDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.CutoverSupportArrangementMapper;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.planv2.query.CutoverSupportContactUpdate;
import cn.iocoder.yudao.module.pms.cutover.dal.dataobject.storage.CutoverPlanSupportContactChangeDO;
import cn.iocoder.yudao.module.pms.cutover.dal.mysql.storage.CutoverPlanSupportContactChangeMapper;
import cn.iocoder.yudao.module.pms.cutover.service.plan.command.PatchApprovedContactCommand;
import cn.iocoder.yudao.module.pms.cutover.service.plan.domain.CutoverRuntimePlanContentCodec;
import cn.iocoder.yudao.module.pms.platform.api.audit.OperationAuditApi;
import lombok.RequiredArgsConstructor;
import lombok.Builder;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class CutoverPlanContactPolicy {
    private final ProjectContactReferenceApi contacts;
    private final CutoverSupportArrangementMapper arrangements;
    private final CutoverPlanSupportContactChangeMapper changes;
    private final CutoverPlanStorage storage;
    private final OperationAuditApi audit;
    public record Selection(Long contactId, Integer contactVersion) { }
    public record Change(String reason, Selection customerContactReference) { }

    public List<ProjectContactReferenceApi.Reference> list(Long tenant, Long actor, Long project) {
        return contacts.list(new ProjectContactReferenceApi.Query(tenant, actor, project));
    }

    public void validate(ProjectContactReferenceApi.Query query, CutoverRuntimePlanContentCodec.DecodedContent content, boolean submitting) {
        if (content.rootSnapshot() == null || !"ONLINE_TEMPLATE_STANDARD".equals(content.rootSnapshot().path("editMode").asText())) return;
        var customers = content.supportArrangements().stream().filter(row -> "CUSTOMER".equals(row.roleCode())).toList();
        if (submitting && customers.isEmpty()) invalid("客户保障安排尚未填写");
        for (var customer : customers) {
            var selection = customer.customerContactReference();
            if (selection == null) {
                if (submitting) invalid("请选择当前项目下启用的客户联系记录");
                continue;
            }
            var fact = lock(query, selection);
            if (!Objects.equals(fact.name(), customer.personName())) invalid("客户姓名已变化，请重新选择联系记录");
            if (submitting || !customer.phone().isBlank()) identity(fact, customer.personName(), customer.phone());
        }
    }

    public void validateChange(CutoverTaskDO task, CutoverSupportArrangementDO arrangement,
                               PatchApprovedContactCommand command, Change change) {
        String role = arrangement.getRoleCode();
        var query = new ProjectContactReferenceApi.Query(command.tenantId(), command.actorId(), task.getProjectId());
        if (change == null || change.reason() == null || change.reason().isBlank() || change.reason().length() > 1000) invalid("请填写联系人变更原因");
        if ("CUSTOMER".equals(role)) {
            if (change.customerContactReference() == null) invalid("请选择启用的项目客户联系记录");
            identity(lock(query, change.customerContactReference()), command.personName(), command.phone());
        }
    }

    @Builder
    public record ChangeWrite(CutoverPlanRevisionDO plan, CutoverSupportArrangementDO baseline,
                              PatchApprovedContactCommand command, Change change, Long newPlanVersion,
                              LocalDateTime time) {}

    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void recordChange(ChangeWrite request) {
        var command = request.command(); var change = request.change(); var baseline = request.baseline();
        var before = storage.currentSupport(baseline);
        var previous = baseline.getCurrentSupportContactChangeId() == null ? null : storage.contactChange(baseline);
        var beforeReference = before.getCustomerContactId() == null ? null
                : new Selection(before.getCustomerContactId(), Math.toIntExact(before.getCustomerContactVersion()));
        var afterReference = "CUSTOMER".equals(baseline.getRoleCode()) && change != null ? change.customerContactReference() : null;
        var row = CutoverPlanSupportContactChangeDO.builder().tenantId(command.tenantId()).supportArrangementId(baseline.getId())
                .changeNo(previous == null ? 1 : previous.getChangeNo() + 1).previousChangeId(baseline.getCurrentSupportContactChangeId())
                .beforePersonName(before.getPersonName()).beforePhone(before.getPhone()).beforeArriveTime(before.getArrivalTime())
                .afterPersonName(command.personName()).afterPhone(command.phone()).afterArriveTime(command.arrivalTime())
                .beforeCustomerContactId(beforeReference == null ? null : beforeReference.contactId())
                .beforeCustomerContactVersion(beforeReference == null ? null : beforeReference.contactVersion().longValue())
                .afterCustomerContactId(afterReference == null ? null : afterReference.contactId())
                .afterCustomerContactVersion(afterReference == null ? null : afterReference.contactVersion().longValue())
                .reason(change == null ? null : change.reason()).operateUserId(command.actorId()).operateTime(request.time())
                .createUserId(command.actorId()).createTime(request.time()).build();
        if (changes.insert(row) != 1) invalid("联系人变更事实保存失败");
        if (arrangements.updateApprovedContactIfMatch(new CutoverSupportContactUpdate(
                request.plan(), baseline, row, request.newPlanVersion())) != 1) invalid("批准方案联系人版本已变化");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", change == null ? null : change.reason()); detail.put("roleCode", before.getRoleCode());
        detail.put("before", Map.of("name", before.getPersonName(), "phone", before.getPhone(), "arrivalTime", before.getArrivalTime()));
        detail.put("after", Map.of("name", command.personName(), "phone", command.phone(), "arrivalTime", command.arrivalTime()));
        if ("CUSTOMER".equals(before.getRoleCode())) {
            detail.put("beforeReference", beforeReference);
            detail.put("afterReference", afterReference);
        }
        detail.put("contactChangeId", row.getId());
        audit.record(command.tenantId(), command.actorId(), command.correlationId(), "CUTOVER_APPROVED_CONTACT_CHANGE",
                "CutoverSupportArrangement", String.valueOf(before.getId()), "SUCCESS", detail);
    }

    public static Selection selection(JsonNode value) {
        if (value == null || value.isNull()) return null;
        try {
            if (!value.isObject() || value.size() != 2 || !value.has("contactId") || !value.has("contactVersion")) invalid("联系人引用格式无效");
            long id = Long.parseLong(value.path("contactId").asText());
            int version = Integer.parseInt(value.path("contactVersion").asText());
            if (id <= 0 || version < 0) invalid("联系人引用版本无效");
            return new Selection(id, version);
        } catch (NumberFormatException invalid) { throw new IllegalArgumentException("联系人引用格式无效"); }
    }
    private ProjectContactReferenceApi.Reference lock(ProjectContactReferenceApi.Query query, Selection selection) {
        return contacts.lockAndInspect(new ProjectContactReferenceApi.Revalidation(query.tenantId(), query.actorId(), query.projectId(), selection.contactId(), selection.contactVersion()));
    }
    private static void identity(ProjectContactReferenceApi.Reference fact, String name, String phone) {
        if (!Objects.equals(fact.name(), name) || phone == null || phone.isBlank()
                || !(Objects.equals(fact.mobile(), phone) || Objects.equals(fact.phone(), phone))) invalid("客户姓名或联系方式已变化，请重新选择联系记录");
    }
    private static void invalid(String message) { throw new IllegalArgumentException(message); }
}
