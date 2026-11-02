package cn.iocoder.yudao.module.pms.project.service.legacydetail;

import cn.hutool.crypto.digest.DigestUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMemberAssignmentMapper;
import cn.iocoder.yudao.module.pms.project.service.projectmanual.ProjectManualCreationService.ProjectAccessActor;
import cn.iocoder.yudao.module.pms.project.service.projectmember.ProjectMemberQualification;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.system.api.user.ActiveUserSelectionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberRoles.*;

/** 专用迁移Owner的只读预览；允许闭环项目核验，不调用正常指派或改生命周期。 */
@Service
@RequiredArgsConstructor
public class ProjectMemberHistoryMigrationService {
    private final ProjectLegacyEntityMigrationService sources;
    private final ProjectMasterMapper projects;
    private final ProjectMemberAssignmentMapper members;
    private final SystemUserMigrationApi identities;
    private final ActiveUserSelectionApi qualification;
    private static final Set<String> FIELDS = Set.of("id", "projectId", "projectType", "memberRole", "memberCode",
            "memberName", "phoneNum", "email", "fromFlag", "createTime", "createBy", "updateTime", "updateBy",
            "effectiveFrom", "effectiveTo");
    public record CurrentMember(Long id, Long userId, String name, String role, String assignmentType,
                                LocalDateTime effectiveFrom, LocalDateTime effectiveTo) { }
    public record Candidate(String sourceKey, String memberCode, String memberName, String role, Long userId,
                            boolean effective, boolean qualified, String result, List<CurrentMember> currentRoleMembers) { }
    public record Report(Long projectId, Long projectVersion, String lifecycleStatus, Long currentManagerId,
                         LocalDateTime evaluatedAt, List<Candidate> candidates) { }

    @Transactional(rollbackFor = Exception.class) // 来源Owner使用FOR UPDATE锁保护当前快照；本方法不执行业务写入。
    public Report preview(Long projectId, ProjectLegacyEntityMigrationService.Request request, ProjectAccessActor actor) {
        var loaded = sources.loadCorrectionSource(projectId, request, actor);
        var project = projects.selectById(projectId);
        if (project == null || !Objects.equals(project.getTenantId(), actor.tenantId())) fail("PROJECT_INACCESSIBLE");
        if (!Set.of("ACTIVE", "NORMAL_CLOSED", "NO_TRACKING_CLOSED", "EXCEPTION_CLOSED").contains(project.getLifecycleStatus()))
            fail("PROJECT_LIFECYCLE_NOT_APPROVED");
        var values = verifiedSourceValues(loaded);
        var names = new LinkedHashSet<String>();
        for (var value : values) {
            if (value.get("memberCode") != null && !value.get("memberCode").isEmpty()) names.add(value.get("memberCode"));
        }
        var accounts = new HashMap<String, Long>();
        var selectedNames = new ArrayList<>(names);
        for (int from = 0; from < selectedNames.size(); from += 200) {
            var part = Set.copyOf(selectedNames.subList(from, Math.min(from + 200, selectedNames.size())));
            for (var person : identities.getHistoricalIdentitiesByUsername(actor.tenantId(), part)) {
                if (!part.contains(person.username()) || accounts.putIfAbsent(person.username(), person.id()) != null)
                    fail("USER_IDENTITY_AMBIGUOUS");
            }
        }
        var now = LocalDateTime.now();
        var current = members.selectListByProjectId(projectId).stream()
                .filter(row -> Objects.equals(row.getTenantId(), actor.tenantId()) && Boolean.TRUE.equals(row.getAuthorizationConfirmed())
                        && "ACTIVE".equals(row.getStatus()) && (row.getEffectiveFrom() == null || !row.getEffectiveFrom().isAfter(now))
                        && (row.getEffectiveTo() == null || row.getEffectiveTo().isAfter(now)))
                .map(row -> new CurrentMember(row.getId(), row.getUserId(), row.getMemberName(), normalize(row.getMemberRole()),
                        row.getAssignmentType(), row.getEffectiveFrom(), row.getEffectiveTo())).toList();
        var result = new ArrayList<Candidate>();
        for (var value : values) {
            String role = "10".equals(value.get("projectType")) && value.get("memberRole") != null ? switch (value.get("memberRole")) {
                case "10" -> SALES_REPRESENTATIVE;
                case "20" -> SERVICE_MANAGER;
                case "30" -> PROJECT_MANAGER;
                default -> null;
            } : null;
            var begin = date(value.get("effectiveFrom"));
            var end = date(value.get("effectiveTo"));
            boolean effective = begin != null && !begin.isAfter(now) && (end == null || end.isAfter(now));
            Long userId = accounts.get(value.get("memberCode"));
            boolean qualified = false;
            String reason;
            if (role == null) reason = "60".equals(value.get("memberRole")) ? "CONTACT_SUBJECT_PENDING" : "ROLE_SUBJECT_PENDING";
            else if (!effective) reason = "EXPIRED_HISTORY";
            else if (userId == null) reason = "NO_EXACT_ACCOUNT";
            else {
                var page = qualification.page(ProjectMemberQualification.query(project, role, 1, 1, null, Set.of(userId)));
                qualified = page.getTotal() == 1 && page.getList().size() == 1 && Objects.equals(page.getList().getFirst().id(), userId);
                reason = qualified ? "OWNER_QUALIFIED" : "OWNER_NOT_QUALIFIED";
                if (qualified && date(value.get("createTime")) == null) reason = "CONFIRMED_AUDIT_TIME_MISSING";
            }
            var sameRole = role == null ? List.<CurrentMember>of() : current.stream().filter(row -> role.equals(row.role())).toList();
            if (qualified && sameRole.stream().anyMatch(row -> !Objects.equals(row.userId(), userId))) reason = "CURRENT_ROLE_CONFLICT";
            result.add(new Candidate(value.get("id"), value.get("memberCode"), value.get("memberName"), role, userId,
                    effective, qualified, reason, sameRole));
        }
        return new Report(projectId, project.getVersion(), project.getLifecycleStatus(), project.getManagerId(), now, List.copyOf(result));
    }
    static List<Map<String,String>> verifiedSourceValues(ProjectLegacyEntityMigrationService.Loaded loaded) {
        var rows=loaded.domains().get("members");if(rows==null)fail("SOURCE_MEMBERS_MISSING");
        var values=new ArrayList<Map<String,String>>();
        for(var row:rows) {
            Map<String,String> value=JsonUtils.parseObject(row.getPayloadJson(),new TypeReference<>(){});
            var canonical=new TreeMap<String,Object>();
            canonical.put("domain",row.getDomainCode());canonical.put("key",row.getSourceKey());
            canonical.put("parentDomain",row.getParentDomain());canonical.put("parentKey",row.getParentSourceKey());
            canonical.put("sourceUpdatedAt",row.getSourceUpdatedAt());canonical.put("values",value==null?null:new TreeMap<>(value));
            if(value==null || !value.keySet().equals(FIELDS)
                    || !Objects.equals(DigestUtil.sha256Hex(JsonUtils.toJsonString(canonical)),row.getChecksum())
                    || !Objects.equals(row.getSourceKey(),value.get("id"))
                    || !Objects.equals(loaded.source().getSourceProjectKey(),value.get("projectId")))fail("SOURCE_INVALID");
            values.add(value);
        }
        return List.copyOf(values);
    }
    static LocalDateTime date(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value.replace(' ', 'T'));
    }
    private static void fail(String code) { throw new IllegalArgumentException("DPPMS_MEMBER_" + code); }
}
