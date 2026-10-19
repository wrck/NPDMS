package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.customer.api.contact.FinalCustomerContactMigrationApi;
import cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi;
import cn.iocoder.yudao.module.pms.platform.api.migration.dto.*;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.service.legacydetail.ProjectMemberHistoryMigrationService.*;
import static cn.iocoder.yudao.module.pms.platform.api.command.PlatformCommandExecutionApi.*;

/** 一次性联系关系承接；来源证据、CUS业务实体与命令回执同事务。 */
@Service @RequiredArgsConstructor
public class ProjectFinalCustomerContactImportService {
    private final ProjectLegacyEntityMigrationService sources;
    private final ProjectMasterMapper projects;
    private final FinalCustomerContactMigrationApi contacts;
    private final SystemUserMigrationApi identities;
    private final ProjectLegacyNativeEvidenceService evidence;
    private final PlatformCommandExecutionApi commands;
    public record Request(@NotNull @Valid ProjectLegacyEntityMigrationService.Request source,
            @NotNull @PositiveOrZero Long expectedProjectVersion,@NotNull @Positive Long projectPartyId,
            @NotBlank @Pattern(regexp="[0-9a-f]{64}") String existingContactsChecksum) { }
    public record Preview(Long projectId,Long projectVersion,int sourceContactCount,FinalCustomerContactMigrationApi.Preview owner) { }
    public record Report(Long sourceId,Long snapshotId,Long projectPartyId,FinalCustomerContactMigrationApi.Result result) { }
    public record Result(String decision,Report report) { }

    @Transactional(rollbackFor=Exception.class)
    public Preview preview(Long projectId,ProjectLegacyEntityMigrationService.Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request,actor);
        var originals=originals(loaded,actor);var project=projects.selectByIdForUpdate(projectId);
        if(project==null || !Objects.equals(project.getTenantId(),actor.tenantId()))fail("PROJECT_INACCESSIBLE");
        var owner=contacts.preview(new FinalCustomerContactMigrationApi.Query(actor.tenantId(),actor.actorId(),projectId,project.getVersion()));
        if(!Objects.equals(owner.finalCustomer().sourceProjectKey(),loaded.source().getSourceProjectKey()))fail("PARTY_SOURCE_CHANGED");
        return new Preview(projectId,project.getVersion(),originals.size(),owner);
    }

    @Transactional(rollbackFor=Exception.class)
    public Result migrate(Long projectId,Request request,ProjectAccessActor actor) {
        var loaded=sources.loadCorrectionSource(projectId,request.source(),actor);var originals=originals(loaded,actor);
        if(originals.isEmpty())fail("SOURCE_CONTACTS_EMPTY");
        var query=new FinalCustomerContactMigrationApi.Query(actor.tenantId(),actor.actorId(),projectId,request.expectedProjectVersion());
        String key="DPPMS-FINAL-CONTACT-INITIAL:"+projectId+":"+request.source().sourceId();
        String digest=DigestUtil.sha256Hex(JsonUtils.toJsonString(List.of(projectId,request.source(),request.expectedProjectVersion(),request.projectPartyId(),request.existingContactsChecksum())));
        var execution=commands.execute(new IdempotencyScope(actor.tenantId(),"PROJ:DPPMS:FINAL_CONTACT_INITIAL",0L,key),digest,Report.class,()->{
            var result=contacts.carry(new FinalCustomerContactMigrationApi.Command(query,request.projectPartyId(),request.existingContactsChecksum(),originals));
            var byKey=new LinkedHashMap<String,FinalCustomerContactMigrationApi.Target>();
            result.targets().forEach(target->{if(byKey.put(target.sourceKey(),target)!=null)fail("OWNER_DUPLICATE");});
            var expected=originals.stream().map(FinalCustomerContactMigrationApi.HistoricalContact::sourceKey).collect(java.util.stream.Collectors.toSet());
            if(!byKey.keySet().equals(expected))fail("OWNER_SOURCE_KEYS_DIFFER");
            var selected=loaded.domains().get("members").stream().filter(row->expected.contains(row.getSourceKey())).toList();
            var outcomes=new LinkedHashMap<String,NativeEntityMigrationRow>();
            byKey.forEach((sourceKey,target)->outcomes.put("members:"+sourceKey,NativeEntityMigrationRow.mapped("members",sourceKey,mapping(target.id()))));
            evidence.reconcile(loaded.source(),loaded.snapshot(),"members",selected,outcomes,"FINAL_CUSTOMER_CONTACT_V1");
            contacts.verify(query,result);
            return new Report(request.source().sourceId(),request.source().snapshotId(),request.projectPartyId(),result);
        },report->new SuccessFacts("PROJ_DPPMS_FINAL_CONTACT_INITIAL","Project",projectId.toString(),key,
                JsonUtils.toJsonString(Map.of("operatorUserId",actor.actorId(),"report",report)),null,null));
        if(execution.decision()==Decision.CONFLICT || execution.decision()==Decision.IN_PROGRESS)fail(execution.decision().name());
        if(execution.decision()==Decision.REPLAY_COMPLETED) {
            contacts.verify(query,execution.response().result());
            var keys=originals.stream().map(FinalCustomerContactMigrationApi.HistoricalContact::sourceKey).collect(java.util.stream.Collectors.toSet());
            var selected=loaded.domains().get("members").stream().filter(row->keys.contains(row.getSourceKey())).toList();
            var statuses=evidence.statuses(actor.tenantId(),loaded.source().getSourceProjectKey(),loaded.snapshot().getId(),selected);
            for(var target:execution.response().result().targets()) {
                var status=statuses.get(target.sourceKey());
                if(status==null || !"MAPPED".equals(status.classification()) || !status.targets().equals(List.of(mapping(target.id()))))fail("REPLAY_BINDING_CHANGED");
            }
        }
        return new Result(execution.decision().name(),execution.response());
    }

    private List<FinalCustomerContactMigrationApi.HistoricalContact> originals(ProjectLegacyEntityMigrationService.Loaded loaded,ProjectAccessActor actor) {
        var values=verifiedSourceValues(loaded).stream().filter(raw->"60".equals(raw.get("memberRole"))).toList();
        var names=new HashSet<String>();values.forEach(raw->{for(String key:List.of("createBy","updateBy"))if(raw.get(key)!=null && !raw.get(key).isEmpty())names.add(raw.get(key));});
        var accounts=new HashMap<String,Long>();var all=new ArrayList<>(names);
        for(int from=0;from<all.size();from+=200)for(var account:identities.getHistoricalIdentitiesByUsername(actor.tenantId(),Set.copyOf(all.subList(from,Math.min(from+200,all.size()))))) {
            if(!names.contains(account.username()) || accounts.putIfAbsent(account.username(),account.id())!=null)fail("AUDIT_ACCOUNT_AMBIGUOUS");
        }
        return values.stream().map(raw->new FinalCustomerContactMigrationApi.HistoricalContact(raw.get("id"),raw.get("projectId"),raw.get("projectType"),raw.get("memberRole"),
                raw.get("memberCode"),raw.get("memberName"),raw.get("phoneNum"),raw.get("email"),raw.get("fromFlag"),
                date(raw.get("createTime")),actorCode(raw.get("createBy"),accounts),date(raw.get("updateTime")),actorCode(raw.get("updateBy"),accounts),
                date(raw.get("effectiveFrom")),date(raw.get("effectiveTo")))).toList();
    }
    private static String actorCode(String code,Map<String,Long> accounts) {
        if(code==null || code.isEmpty())return code;var id=accounts.get(code);if(id==null)fail("AUDIT_ACCOUNT_PENDING");return id.toString();
    }
    private static ExternalTargetMapping mapping(Long id){return new ExternalTargetMapping("CUS","ProjectCustomerContact","cus_project_customer_contact_relation",id,"FINAL_CUSTOMER_CONTACT",0);}
    private static void fail(String reason){throw new IllegalArgumentException("DPPMS_FINAL_CONTACT_"+reason);}
}
