package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.system.api.company.CompanyApi;
import cn.iocoder.yudao.module.system.api.migration.SystemDepartmentMigrationApi;
import cn.iocoder.yudao.module.system.api.dept.PostApi;
import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;
import cn.iocoder.yudao.module.pms.platform.support.migration.DppmsCompanyCodeConverter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.time.LocalDateTime;
import java.util.*;
import static cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi.*;

/** Source interpretation only. All formal account writes remain in the SYSTEM Owner. */
@Component
@RequiredArgsConstructor
public class DppmsUserSyncAdapter implements DataSyncAdapter {
    private final SystemUserMigrationApi users;
    private final SyncTaskService tasks;
    private final CompanyApi companies;
    private final SystemDepartmentMigrationApi departments;
    private final PostApi posts;
    private final Validator validator;
    private record EmailValue(@Email String value) {}
    private static final Map<String, String> EHR_NAME_EXCEPTIONS = Map.of("w00343", "74", "j04650", "4804", "l02826", "2770");

    @Override public Descriptor descriptor() {
        return new Descriptor("DPPMS_USERS", "DPPMS正式用户迁移", DppmsUserSyncTemplate.COLUMNS.keySet().stream().sorted().map(table -> {
            List<String> names = new ArrayList<>(DppmsUserSyncTemplate.COLUMNS.get(table));
            names.addAll("t_user_info".equals(table) ? DppmsUserSyncTemplate.EMPLOYMENT_REFERENCES : DppmsUserSyncTemplate.ACCOUNT_REFERENCES);
            return new ObjectDescriptor(table, table, names.stream().map(name -> new Field(name, name, "STRING", false)).toList(),
                    "SYSTEM", "User", "system_users", false);
        }).toList(), List.of("RETAIN"), List.of("UPSERT"), false);
    }
    @Override public boolean sharesTargetAcrossSources() { return true; }
    @Override public List<Change> preview(Batch batch) { return execute(batch, false); }
    @Override public List<Change> apply(Batch batch) { return execute(batch, true); }
    @Override public void refreshCaches() { }

    private List<Change> execute(Batch batch, boolean apply) {
        if (!batch.full() || batch.clearBeforeLoad() || !"UPSERT".equals(batch.loadingMode()) || !"RETAIN".equals(batch.missingPolicy()))
            throw new IllegalArgumentException("用户迁移必须整批核对全部账号与任职资料，不支持清空、分块或删除来源缺失账号");
        long tenant = TenantContextHolder.getRequiredTenantId();
        String[] owner = batch.owner() == null ? new String[0] : batch.owner().split(":");
        if (owner.length != 3 || !"integration".equals(owner[0]) || !Long.toString(tenant).equals(owner[1]))
            throw new IllegalArgumentException("用户迁移所有权无效");
        var task = tasks.required(Long.parseLong(owner[2]));
        if (!"DPPMS".equals(task.getSourceSystem()) || !"DPPMS_USERS".equals(task.getAdapter()))
            throw new IllegalArgumentException("用户迁移不能混用来源任务");
        Map<String, Row> sources = new LinkedHashMap<>();
        Map<String, Binding> prior = new HashMap<>();
        for (Binding binding : batch.bindings()) if (prior.put(key(binding), binding) != null)
            throw new IllegalArgumentException("用户来源绑定重复");
        Map<String, Row> tById = new HashMap<>();
        Map<String, List<Row>> accounts = new TreeMap<>();
        Map<String, List<Row>> employments = new HashMap<>();
        for (Row row : batch.rows()) {
            List<String> columns = DppmsUserSyncTemplate.COLUMNS.get(row.object());
            if (columns == null || !row.fields().keySet().containsAll(columns) || sources.put(key(row), row) != null)
                throw new IllegalArgumentException("用户来源对象、原字段或身份缺失/重复");
            String sourceKey = text(row.fields(), "t_user".equals(row.object()) ? "user_id" : "id");
            if (!Objects.equals(sourceKey, row.sourceKey())) throw new IllegalArgumentException("用户来源提取键与原主键不同");
            if ("t_user_info".equals(row.object())) employments.computeIfAbsent(text(row.fields(), "user_id"), ignored -> new ArrayList<>()).add(row);
            else {
                if ("t_user".equals(row.object())) tById.put(row.sourceKey(), row);
                String username = username(row);
                if (DppmsUserMigrationScope.includes(username)) accounts.computeIfAbsent(username, ignored -> new ArrayList<>()).add(row);
            }
        }
        List<Entry> entries = new ArrayList<>();
        List<AuditReference> auditReferences = new ArrayList<>();
        Set<String> carried = new HashSet<>();
        for (Row row : sources.values()) if (!"t_user_info".equals(row.object()) && "admin".equals(username(row))) {
            boolean adjudicated = "t_user".equals(row.object()) && "0".equals(row.sourceKey())
                    || "fnd_user_info".equals(row.object()) && "81".equals(row.sourceKey());
            if (!adjudicated) throw new IllegalArgumentException("旧管理员来源身份变化，需重新裁决审计关联");
            Binding previous = prior.get(key(row));
            auditReferences.add(new AuditReference(row.object(), row.sourceKey(), 1L, "admin", previous == null ? null : previous.targetId()));
            carried.add(key(row));
        }
        Map<String, Long> departmentCache = new HashMap<>(), postCache = new HashMap<>(), companyCache = new HashMap<>();
        Map<String, Set<Long>> employeeUsers = employeeUsers(accounts, employments, prior);
        for (var group : accounts.entrySet()) {
            List<Row> aliases = group.getValue();
            Row fnd = primaryFnd(group.getKey(), aliases);
            List<Row> legacyRows = aliases.stream().filter(row -> "t_user".equals(row.object())).toList();
            if (legacyRows.size() > 1) throw new IllegalArgumentException("同名t_user没有重复身份合并裁决");
            Row legacy = legacyRows.isEmpty() ? null : legacyRows.getFirst();
            Row primary = fnd == null ? legacy : fnd;
            List<Row> details = legacy == null ? List.of() : employments.getOrDefault(legacy.sourceKey(), List.of());
            List<DppmsUserCustomInfo.Employment> assignments = new ArrayList<>();
            Set<Long> departmentIds = new HashSet<>(), postIds = new HashSet<>();
            Set<String> verifiedWorkNos = new HashSet<>();
            for (Row account : aliases) {
                String workNo = verifiedLoginWorkNo(account);
                if (workNo != null) verifiedWorkNos.add(workNo);
            }
            for (Row detail : details) {
                Map<String, Object> values = detail.fields();
                String companyCode = DppmsCompanyCodeConverter.fromSource(text(values, "migrationCompanyCode"));
                Long companyId = companyCache.computeIfAbsent(companyCode, code -> {
                    var company = companies.getCompanyByCode(code);
                    if (company == null) throw new IllegalArgumentException("转换后的所属公司尚未建立正式业务目录");
                    return company.getId();
                });
                String departmentCode = text(values, "migrationDepartmentCode");
                Long departmentId = departmentId(departmentCode, departmentCache);
                if (departmentId != null) departmentIds.add(departmentId);
                String positionCode = text(values, "migrationPositionCode");
                Long postId = postId(positionCode, postCache);
                if (postId != null) postIds.add(postId);
                Object employeeId = values.get("migrationVerifiedEmployeeId");
                if (employeeId != null) verifiedWorkNos.add(text(values, "workNo"));
                assignments.add(new DppmsUserCustomInfo.Employment(values, companyCode, companyId, departmentCode, departmentId, postId,
                        employeeId, personnel(employeeId, employeeUsers), personnel(values.get("reportTo"), employeeUsers),
                        personnel(values.get("wfreportTo"), employeeUsers)));
            }
            if (departmentIds.size() > 1 || verifiedWorkNos.size() > 1)
                throw new IllegalArgumentException("同一账号主部门或真实工号存在冲突，须逐项裁决，不能任选任职");
            String nickname = fnd == null ? null : nonblank(text(fnd.fields(), "realName"));
            if (EHR_NAME_EXCEPTIONS.containsKey(group.getKey())) {
                if (fnd == null || !EHR_NAME_EXCEPTIONS.get(group.getKey()).equals(text(fnd.fields(), "migrationEmployeeId")))
                    throw new IllegalArgumentException("已裁决EHR同人例外的员工外键变化");
                nickname = nonblank(text(fnd.fields(), "migrationEmployeeName"));
            }
            if (nickname == null) nickname = attribute(details, "realName");
            String email = fnd == null ? null : nonblank(text(fnd.fields(), "email"));
            if (email == null) email = attribute(details, "email");
            if ("zhaoyangxi_supp".equals(group.getKey()) && email != null) email = email.replaceAll("\\t+$", "");
            if (email != null && !validator.validate(new EmailValue(email)).isEmpty() && !("w30049".equals(group.getKey()) && "@dptech.com".equals(email)))
                throw new IllegalArgumentException("账号邮箱格式不在已裁决例外，不能改写或丢弃：" + group.getKey());
            JsonNode json = DppmsUserCustomInfo.assemble(fnd == null ? null : fnd.fields(), legacy == null ? null : legacy.fields(), assignments);
            JsonNode phone = json.path("customInfo").path("phone");
            String mobile = phone.isMissingNode() || phone.isNull() ? null : nonblank(phone.asText());
            if (mobile == null) mobile = attribute(details, "mobile");
            if (mobile != null) mobile = mobile.replace(" ", "");
            if (mobile != null && !mobile.matches("1[3-9][0-9]{9}")) throw new IllegalArgumentException("手机号不能合法承接，须裁决");
            String sex = attribute(details, "sex");
            Integer formalSex = sex == null ? null : switch (sex) { case "1" -> 1; case "0" -> 2; default -> throw new IllegalArgumentException("来源性别枚举未知"); };
            Profile profile = new Profile(group.getKey(), nickname, attribute(details, "remark"), departmentIds.isEmpty() ? null : departmentIds.iterator().next(),
                    Set.copyOf(postIds), email, mobile, formalSex, attribute(details, "avatar"), status(primary),
                    verifiedWorkNos.isEmpty() ? null : verifiedWorkNos.iterator().next(), json);
            SourceAudit audit = audit(primary, sources);
            Binding previous = prior.get(key(primary));
            User last = previous == null ? null : lastApplied(previous);
            entries.add(new Entry(primary.object(), primary.sourceKey(), profile, audit, primary.targetId(), last, false, null));
            carried.add(key(primary));
            List<Row> references = new ArrayList<>(aliases); references.remove(primary); references.addAll(details);
            for (Row reference : references) {
                Binding previousAlias = prior.get(key(reference));
                entries.add(new Entry(reference.object(), reference.sourceKey(), profile, audit, reference.targetId(),
                        previousAlias == null ? null : lastApplied(previousAlias), true, null));
                carried.add(key(reference));
            }
        }
        var request = new Request(tenant, batch.owner(), entries, batch.adoptExisting(), auditReferences);
        List<Result> results = apply ? users.apply(request) : users.preview(request);
        List<Change> changes = new ArrayList<>();
        for (Result result : results) {
            Map<String, Object> after = fields(result.after());
            after.put("_auditOnly", "REFERENCED".equals(result.action()));
            changes.add(new Change(result.object(), result.sourceKey(), result.after() == null ? null : result.after().id(),
                    result.action(), fields(result.before()), after, result.message()));
        }
        for (Row source : sources.values()) if (!carried.contains(key(source))) {
            boolean orphan = "t_user_info".equals(source.object()) && !tById.containsKey(text(source.fields(), "user_id"));
            changes.add(new Change(source.object(), source.sourceKey(), null, "SKIPPED", Map.of(), Map.of(),
                    DppmsUserMigrationScope.excludesAdjudicatedOrphan(source) ? "用户明确本条测试/管理员资料不迁入：无账号外键且不在已批准账号范围" :
                    orphan ? "无来源账号关联的人员资料，登记待关联；不伪造账号" : "用户限定仅迁移首字母+工号、*_ss、*_supp；原字符串不满足，按裁决排除且不改名"));
        }
        return List.copyOf(changes);
    }
    private Long departmentId(String code, Map<String, Long> cache) {
        if (code == null || code.isBlank()) return null;
        if (cache.isEmpty()) {
            // Historical employment may reference a disabled department; the SYSTEM Owner still
            // validates tenant and existence. Interactive selection and permissions remain separate.
            for (var department : departments.getCatalogue()) {
                if (department.code() != null && cache.put(department.code(), department.id()) != null)
                    throw new IllegalArgumentException("正式部门编码重复，历史任职不能任选关联");
            }
        }
        if (!cache.containsKey(code))
            throw new IllegalArgumentException("历史任职部门尚未关联正式目录，不能丢失来源部门或编造引用");
        return cache.get(code);
    }
    private Long postId(String code, Map<String, Long> cache) {
        if (code == null || code.isBlank()) return null;
        if (!cache.containsKey(code)) { var found = posts.getPostByCode(code); cache.put(code, found == null ? null : found.getId()); }
        return cache.get(code);
    }
    static Row primaryFnd(String username, List<Row> aliases) {
        List<Row> values = aliases.stream().filter(row -> "fnd_user_info".equals(row.object())).toList();
        if (values.isEmpty()) return null;
        if (values.size() == 1) return values.getFirst();
        Set<String> ids = new HashSet<>(); values.forEach(row -> ids.add(row.sourceKey()));
        String primary = "c00733".equals(username) && ids.equals(Set.of("233", "408")) ? "408"
                : "w02459".equals(username) && ids.equals(Set.of("118", "119")) ? "119" : null;
        if (primary == null || values.stream().anyMatch(row -> status(row) != 1))
            throw new IllegalArgumentException("同名fnd账号没有停用身份合并裁决");
        return values.stream().filter(row -> primary.equals(row.sourceKey())).findFirst().orElseThrow();
    }
    private static String verifiedLoginWorkNo(Row row) {
        Object count = row.fields().get("migrationLoginCount");
        if (count == null) return null;
        if (!"1".equals(count.toString())) throw new IllegalArgumentException("EHR登录目录同名不唯一，不按任意员工关联");
        String employee = text(row.fields(), "migrationEmployeeId");
        String workNo = nonblank(text(row.fields(), "migrationEmployeeWorkNo"));
        if (employee == null || workNo == null || !employee.equals(text(row.fields(), "migrationLoginEmployeeId"))
                || !workNo.equals(text(row.fields(), "migrationLoginWorkNo")))
            throw new IllegalArgumentException("EHR员工外键或工号不一致，不能凭登录名猜工号");
        boolean exception = employee.equals(EHR_NAME_EXCEPTIONS.get(username(row)));
        if (!exception && (!Objects.equals(text(row.fields(), "migrationEmployeeName"), text(row.fields(), "migrationLoginName"))
                || "fnd_user_info".equals(row.object()) && !Objects.equals(text(row.fields(), "realName"), text(row.fields(), "migrationEmployeeName"))))
            throw new IllegalArgumentException("来源账号与EHR员工姓名冲突，须裁决同人后再关联工号");
        return workNo;
    }
    private static Map<String, Set<Long>> employeeUsers(Map<String, List<Row>> accounts, Map<String, List<Row>> employments, Map<String, Binding> prior) {
        Map<String, Set<Long>> result = new HashMap<>();
        for (List<Row> rows : accounts.values()) for (Row row : rows) {
            Binding binding = prior.get(key(row));
            if (binding == null || binding.targetId() == null || Boolean.TRUE.equals(binding.lastFields().get("_auditOnly"))) continue;
            if (verifiedLoginWorkNo(row) != null) result.computeIfAbsent(text(row.fields(), "migrationEmployeeId"), ignored -> new HashSet<>()).add(binding.targetId());
            if ("t_user".equals(row.object())) for (Row detail : employments.getOrDefault(row.sourceKey(), List.of())) {
                String employeeId = text(detail.fields(), "migrationVerifiedEmployeeId");
                if (employeeId != null) result.computeIfAbsent(employeeId, ignored -> new HashSet<>()).add(binding.targetId());
            }
        }
        return result;
    }
    private static DppmsUserCustomInfo.PersonnelReference personnel(Object employeeId, Map<String, Set<Long>> users) {
        Set<Long> current = users.get(Objects.toString(employeeId, null));
        return employeeId == null ? null : new DppmsUserCustomInfo.PersonnelReference(employeeId,
                current != null && current.size() == 1 ? current.iterator().next() : null);
    }
    private static SourceAudit audit(Row primary, Map<String, Row> sources) {
        boolean fnd = "fnd_user_info".equals(primary.object());
        LocalDateTime created = time(primary.fields().get(fnd ? "createTime" : "create_time"));
        LocalDateTime updated = time(primary.fields().get(fnd ? "updateTime" : "update_time"));
        return new SourceAudit(actor(primary, fnd ? "createBy" : "create_by", sources), created,
                actor(primary, fnd ? "updateBy" : "update_by", sources), updated == null ? created : updated);
    }
    private static String actor(Row source, String field, Map<String, Row> sources) {
        String name = text(source.fields(), field);
        if (name == null || name.isEmpty()) return name;
        List<Row> matches = sources.values().stream().filter(row -> source.object().equals(row.object()) && name.equals(username(row))).toList();
        if (matches.size() > 1) return key(Objects.requireNonNull(primaryFnd(name, matches)));
        if (matches.size() != 1) throw new IllegalArgumentException("历史操作者未精确关联来源账号，须裁决");
        return key(matches.getFirst());
    }
    private static String attribute(List<Row> rows, String field) {
        List<String> values = rows.stream().map(row -> nonblank(text(row.fields(), field))).filter(Objects::nonNull).distinct().toList();
        if (values.size() > 1) throw new IllegalArgumentException("多公司任职的" + field + "存在冲突，不能任选主资料");
        return values.isEmpty() ? null : values.getFirst();
    }
    private static int status(Row row) {
        return switch (text(row.fields(), "status")) {
            case "1" -> 0; case "0" -> 1; case "2" -> {
                if (!"t_user".equals(row.object())) throw new IllegalArgumentException("fnd状态2未裁决"); yield 1;
            }
            default -> throw new IllegalArgumentException("账号来源状态未知，不能擅自启用");
        };
    }
    static User lastApplied(Binding binding) {
        if (Boolean.TRUE.equals(binding.lastFields().get("_auditOnly"))) throw new IllegalArgumentException("审计专用绑定不能转为账号资料承接");
        Map<String, Object> values = new LinkedHashMap<>(JsonUtils.getObjectMapper().convertValue(binding.lastFields().get("profile"), Map.class));
        Object originalJson = values.remove("customInfo");
        Profile basic = JsonUtils.getObjectMapper().convertValue(values, Profile.class);
        if (originalJson != null && !(originalJson instanceof String)) throw new IllegalArgumentException("账号来源绑定JSON不是无损业务对象文本");
        Profile profile = new Profile(basic.username(), basic.nickname(), basic.remark(), basic.departmentId(), basic.postIds(), basic.email(),
                basic.mobile(), basic.sex(), basic.avatar(), basic.status(), basic.employeeNo(), originalJson == null ? null : JsonUtils.parseTree((String) originalJson));
        Audit audit = JsonUtils.getObjectMapper().convertValue(binding.lastFields().get("audit"), Audit.class);
        return new User(binding.targetId(), profile, audit);
    }
    static Map<String, Object> fields(User user) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (user != null) {
            Map<String, Object> profile = new LinkedHashMap<>(JsonUtils.getObjectMapper().convertValue(user.profile(), Map.class));
            // The shared NON_NULL serializer removes explicit JsonNode null properties. Binding replay must compare the complete object.
            profile.put("customInfo", user.profile().customInfo() == null ? null : user.profile().customInfo().toString());
            result.put("profile", profile); result.put("audit", user.audit());
        }
        return result;
    }
    private static String username(Row row) { return text(row.fields(), "t_user".equals(row.object()) ? "user_name" : "username"); }
    private static String text(Map<String, Object> fields, String name) { return Objects.toString(fields.get(name), null); }
    private static String nonblank(String value) { return value == null || value.isBlank() ? null : value; }
    private static String key(Row row) { return row.object() + ":" + row.sourceKey(); }
    private static String key(Binding binding) { return binding.object() + ":" + binding.sourceKey(); }
    private static LocalDateTime time(Object value) { return value == null ? null : SyncFieldMapper.sourceTime(value); }
}
