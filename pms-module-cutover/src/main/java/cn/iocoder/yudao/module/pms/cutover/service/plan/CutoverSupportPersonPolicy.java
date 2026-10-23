package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.participant.ProjectMemberSelectionApi;
import cn.iocoder.yudao.module.system.api.permission.RoleCandidateFactApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import java.util.*;

/** 人员来源不授予任何授权；只检查当前租户的项目成员或实际启用角色。 */
@Service
@RequiredArgsConstructor
public class CutoverSupportPersonPolicy {
    private final ProjectMemberSelectionApi members;
    private final RoleCandidateFactApi roles;
    private final AdminUserApi users;
    public record Reference(String sourceCode, Long id, Long version) {}
    public record Choice(Reference reference, String name, String phone) {}
    public record Query(Long tenantId, Long actorId, Long projectId, String roleCode) {}
    public record Validation(Query context, Reference reference, String name, boolean submitting) {}
    private static final Map<String, String> SYSTEM_ROLES = Map.of(
            "DP_FIRST_LINE", "PROJECT_MANAGER", "DP_SECOND_LINE", "probSupporter", "DP_RND", "probRD");

    public List<Choice> choices(Query query) {
        identity(query);
        if ("CUSTOMER".equals(query.roleCode())) {
            List<Choice> result = new ArrayList<>();
            for (int page = 1; ; page++) {
                var batch = members.page(ProjectMemberSelectionApi.Query.builder().tenantId(query.tenantId())
                        .actorId(query.actorId()).projectId(query.projectId()).pageNo(page).pageSize(100).role("END_USER").build());
                batch.members().forEach(member -> result.add(new Choice(new Reference("PROJECT_MEMBER", member.assignmentId(), member.version()), member.name(), member.phone())));
                if ((long) page * 100 >= batch.total()) return List.copyOf(result);
            }
        }
        String role = systemRole(query.roleCode());
        var candidates = roles.inspect(new RoleCandidateFactApi.Query(query.tenantId(), role));
        if (candidates.isEmpty()) return List.of();
        var people = users.getUserMap(candidates.stream().map(RoleCandidateFactApi.Candidate::userId).toList());
        return candidates.stream().filter(candidate -> people.containsKey(candidate.userId())).map(candidate -> {
            var person = people.get(candidate.userId());
            return new Choice(new Reference("SYSTEM_USER", person.getId(), watermark(candidate.userWatermark())), person.getNickname(), person.getMobile());
        }).toList();
    }

    public void validate(Validation request) {
        var query = request.context(); identity(query);
        var ref = request.reference();
        if (ref == null) {
            if (request.submitting() && !"CUSTOMER".equals(query.roleCode())) invalid("请选择对应角色的系统用户");
            return;
        }
        String name;
        if ("CUSTOMER".equals(query.roleCode())) {
            if (!"PROJECT_MEMBER".equals(ref.sourceCode())) invalid("客户只能引用当前项目成员");
            try {
                name = members.lockAndInspect(ProjectMemberSelectionApi.Revalidation.builder().tenantId(query.tenantId())
                        .actorId(query.actorId()).projectId(query.projectId()).assignmentId(ref.id()).expectedVersion(ref.version()).role("END_USER").build()).name();
            } catch (IllegalArgumentException staleMember) {
                throw new CutoverPlanApplicationException(CutoverPlanApplicationException.Code.SUPPORT_ARRANGEMENT_INCOMPLETE, staleMember.getMessage());
            }
        } else {
            if (!"SYSTEM_USER".equals(ref.sourceCode())) invalid("工程师必须选择系统用户");
            var membership = roles.lockAndInspectActor(new RoleCandidateFactApi.ActorQuery(query.tenantId(), ref.id())).stream()
                    .filter(member -> systemRole(query.roleCode()).equals(member.roleCode())).findFirst().orElse(null);
            if (membership == null || !Objects.equals(watermark(membership.userWatermark()), ref.version())) invalid("人员角色、启用状态或资料已变化，请重新选择");
            var user = users.getUser(ref.id());
            if (user == null || !Integer.valueOf(0).equals(user.getStatus())) invalid("系统用户已失效，请重新选择");
            name = user.getNickname();
        }
        if (!Objects.equals(name, request.name())) invalid("人员姓名已变化，请重新选择");
    }

    public static Reference reference(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (!node.isObject() || node.size() != 3 || !node.has("sourceCode") || !node.has("id") || !node.has("version")
                || !Set.of("PROJECT_MEMBER", "SYSTEM_USER").contains(node.path("sourceCode").asText())) invalid("人员引用格式无效");
        return new Reference(node.path("sourceCode").asText(), number(node.get("id"), true), number(node.get("version"), false));
    }
    private static Long number(JsonNode node, boolean positive) {
        if (node == null || !(node.isTextual() || node.isIntegralNumber()) || !node.asText().matches("0|[1-9][0-9]*")
                || node.isIntegralNumber() && (!node.canConvertToLong() || node.asLong() >= 9_007_199_254_740_991L)) invalid("人员引用编号或版本无效");
        try { long value = Long.parseLong(node.asText()); if (positive && value == 0) invalid("人员引用编号无效"); return value; }
        catch (NumberFormatException error) { throw new IllegalArgumentException("人员引用编号或版本无效"); }
    }
    private static String systemRole(String role) {
        String result = SYSTEM_ROLES.get(role); if (result == null) invalid("保障角色无效"); return result;
    }
    private static Long watermark(Long value) { return value == null ? 0L : value; }
    private static void identity(Query query) {
        if (query == null || !Objects.equals(query.tenantId(), TenantContextHolder.getTenantId()) || query.actorId() == null || query.projectId() == null)
            throw new SecurityException("人员选择身份无效");
    }
    private static void invalid(String message) { throw new CutoverPlanApplicationException(CutoverPlanApplicationException.Code.SUPPORT_ARRANGEMENT_INCOMPLETE, message); }
}
