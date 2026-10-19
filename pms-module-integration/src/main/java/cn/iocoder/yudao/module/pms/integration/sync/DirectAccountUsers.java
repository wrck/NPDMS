package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.module.system.api.migration.SystemUserMigrationApi;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountRows.UserRow;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountUserMapper;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.UserMigrationReferenceQuery;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountQueries.UserMigrationTenantQuery;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

@RequiredArgsConstructor
/** Approved rule copy from SystemUserMigrationApiImpl; isolated direct persistence, no Owner invocation. */
final class DirectAccountUsers implements SystemUserMigrationApi {
    private final DirectAccountUserMapper users;
    private final cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountAuxiliaryMapper auxiliary;
    private final PasswordEncoder passwordEncoder;
    private final cn.iocoder.yudao.framework.tenant.config.TenantProperties tenantProperties;
    private final cn.iocoder.yudao.framework.common.biz.system.oauth2.OAuth2TokenCommonApi tokens;
    private final DataSource dataSource;

    @Override public List<HistoricalIdentity> getHistoricalIdentities(Long tenantId, Set<Long> ids) { throw new UnsupportedOperationException("Not a direct-task operation"); }
    @Override public List<HistoricalIdentity> getHistoricalIdentitiesByUsername(Long tenantId, Set<String> names) { throw new UnsupportedOperationException("Not a direct-task operation"); }
    @Override public List<MemberIdentity> getHistoricalMemberIdentities(Long tenantId, Set<String> names) { throw new UnsupportedOperationException("Not a direct-task operation"); }
    @Override public List<Result> preview(Request request) {
        validateRequest(request);
        validateReferences(request);
        List<User> current = users.selectProfiles(new UserMigrationTenantQuery(request.tenantId()))
                .stream().map(DirectAccountUsers::snapshot).toList();
        List<Result> results = plan(request, current, false);
        validateQuota(current, results);
        return results;
    }

    @Override     public List<Result> apply(Request request) {
        validateRequest(request);
        requireRepeatableRead();
        List<User> current = users.selectProfilesForUpdate(new UserMigrationTenantQuery(request.tenantId()))
                .stream().map(DirectAccountUsers::snapshot).toList();
        validateReferences(request);
        List<Result> results = plan(request, current, true);
        if (results.stream().anyMatch(result -> "CONFLICT".equals(result.action())))
            throw new IllegalArgumentException("用户迁移身份、业务字段或来源绑定冲突，整批未承接");
        validateQuota(current, results);
        Set<Long> saved = new HashSet<>();
        for (Result result : results) {
            if (!Set.of("CREATED", "UPDATED", "ADOPTED").contains(result.action()) || !saved.add(result.after().id())) continue;
            UserRow entity = entity(request.tenantId(), result.after());
            if ("CREATED".equals(result.action())) {
                // Unique, discarded activation material. No source/shared/default credential is read or stored.
                entity.setPassword(passwordEncoder.encode(UUID.randomUUID().toString() + UUID.randomUUID()));
                if (users.insertHistoricalUser(new DirectAccountUserMapper.Write(entity, result.after().audit())) != 1) throw new IllegalStateException("正式用户创建失败");
            } else if (users.updateHistoricalProfile(new DirectAccountUserMapper.Write(entity, result.after().audit())) != 1) throw new IllegalStateException("正式用户承接目标变化");
            synchronizePosts(result.before(), result.after());
            if (result.before() != null && !Objects.equals(result.before().profile().status(), result.after().profile().status())
                    && Integer.valueOf(1).equals(result.after().profile().status()))
                auxiliary.selectAccessTokens(new cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountAuxiliaryMapper.UserScope(request.tenantId(),result.after().id()))
                        .forEach(tokens::removeAccessToken);
        }
        return results;
    }

    private void validateQuota(List<User> current, List<Result> results) {
        long additions = results.stream().filter(result -> "CREATED".equals(result.action()))
                .map(result -> result.after().profile().username()).distinct().count();
        if (additions == 0) return;
        if (Boolean.TRUE.equals(tenantProperties.getEnable())) {
            Integer quota=auxiliary.selectAccountLimit(new UserMigrationTenantQuery(TenantContextHolder.getRequiredTenantId()));
            if(quota==null || current.size()+additions>quota)
                throw new IllegalArgumentException("历史用户迁入超出既有租户账号容量，须先处理租户额度，未写入用户");
        }
    }

    @Override public List<AuditCorrectionResult> previewAuditCorrection(AuditCorrectionRequest request) { throw new UnsupportedOperationException("Not a direct-task operation"); }
    @Override public List<AuditCorrectionResult> applyAuditCorrection(AuditCorrectionRequest request) { throw new UnsupportedOperationException("Not a direct-task operation"); }
    private void synchronizePosts(User before, User after) {
        Set<Long> previous=before==null?Set.of():posts(before.profile()), next=posts(after.profile());
        var scope=new cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountAuxiliaryMapper.UserScope(TenantContextHolder.getRequiredTenantId(),after.id());
        if(!Set.copyOf(auxiliary.selectPosts(scope)).equals(previous))
            throw new IllegalArgumentException("正式用户岗位关系与账号字段不同，须先裁决，不擅自清空或覆盖");
        Set<Long> removed=new HashSet<>(previous);removed.removeAll(next);
        if(!removed.isEmpty())auxiliary.deletePosts(new cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountAuxiliaryMapper.PostRemoval(scope,removed));
        for(Long post:next)if(!previous.contains(post)) {
            String actor=Objects.toString(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),null);
            auxiliary.insertPost(new cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectAccountAuxiliaryMapper.PostWrite(scope,IdWorker.getId(),post,actor,java.time.LocalDateTime.now()));
        }
    }
    private void validateReferences(Request request) {
        Set<Long> departments = new HashSet<>(), posts = new HashSet<>();
        for (Entry entry : request.entries()) {
            if (entry.profile() == null) continue;
            if (entry.profile().departmentId() != null) departments.add(entry.profile().departmentId());
            posts.addAll(posts(entry.profile()));
        }
        if (!departments.isEmpty() && !departments.equals(Set.copyOf(users.selectDepartmentIds(
                new UserMigrationReferenceQuery(request.tenantId(), departments)))))
            throw new IllegalArgumentException("正式主部门不存在或跨租户，禁止把旧ID当新部门ID");
        if (!posts.isEmpty() && !posts.equals(Set.copyOf(users.selectPostIds(new UserMigrationReferenceQuery(request.tenantId(), posts)))))
            throw new IllegalArgumentException("正式岗位不存在或跨租户，禁止虚造岗位引用");
    }
    private static void validateRequest(Request request) {
        if (request == null || !Objects.equals(TenantContextHolder.getRequiredTenantId(), request.tenantId())
                || request.owner() == null || request.owner().isBlank() || request.entries() == null
                || request.auditReferences() == null || request.entries().size() + request.auditReferences().size() > 10000
                || request.entries().stream().anyMatch(Objects::isNull) || request.auditReferences().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("用户迁移租户、所有权或容量无效");
    }
    private void requireRepeatableRead() {
        requireTransaction();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            if (connection.getTransactionIsolation() != Connection.TRANSACTION_REPEATABLE_READ)
                throw new IllegalStateException("用户迁移需要REPEATABLE_READ保护账号目录与未建立的身份，未写入用户");
        } catch (SQLException exception) {
            throw new IllegalStateException("无法核实用户迁移事务隔离", exception);
        } finally { DataSourceUtils.releaseConnection(connection, dataSource); }
    }

    static List<Result> plan(Request request, List<User> current, boolean allocateIds) {
        Map<String, User> byUsername = new HashMap<>();
        for (User user : current) if (byUsername.put(identity(user.profile().username()), user) != null)
            throw new IllegalArgumentException("正式账号目录存在重复登录名，不能按顺序接管");
        Map<String, Entry> primary = new LinkedHashMap<>();
        Map<String, Entry> identities = new HashMap<>();
        for (Entry entry : request.entries()) {
            if (entry.object() == null || entry.object().isBlank() || entry.sourceKey() == null || entry.sourceKey().isBlank()
                    || entry.profile() == null || entry.audit() == null || identities.put(entry.identity(), entry) != null)
                throw new IllegalArgumentException("用户来源身份、资料或历史审计缺失/重复");
            if (!entry.referenceOnly() && primary.put(identity(entry.profile().username()), entry) != null)
                throw new IllegalArgumentException("同一账号存在多个主资料，必须依据已裁决身份合并");
        }
        Map<String, Long> targets = new HashMap<>();
        Map<String, User> auditReferences = new LinkedHashMap<>();
        for (AuditReference reference : request.auditReferences()) {
            if (reference.object() == null || reference.object().isBlank() || reference.sourceKey() == null || reference.sourceKey().isBlank()
                    || reference.userId() == null || !required(reference.expectedUsername(), 30)
                    || identities.containsKey(reference.identity()) || auditReferences.containsKey(reference.identity()))
                throw new IllegalArgumentException("审计专用来源身份缺失、重复或与迁入账号混用");
            User target = current.stream().filter(value -> reference.userId().equals(value.id())).findFirst().orElse(null);
            if (target == null || !reference.expectedUsername().equals(target.profile().username())
                    || reference.previousTargetId() != null && !reference.previousTargetId().equals(target.id()))
                throw new IllegalArgumentException("审计专用关联目标缺失、跨租户或已变化，不能按名字重新关联");
            auditReferences.put(reference.identity(), target);
        }
        for (var pair : primary.entrySet()) {
            User found = byUsername.get(pair.getKey());
            targets.put(pair.getKey(), found != null ? found.id() : allocateIds ? IdWorker.getId() : null);
        }
        List<Result> results = new ArrayList<>();
        for (Entry entry : request.entries()) {
            String username = identity(entry.profile().username());
            User before = byUsername.get(username);
            Entry canonical = primary.get(username);
            if (canonical == null || !Objects.equals(canonical.profile(), entry.profile()))
                throw new IllegalArgumentException("来源别名没有完整一致的已裁决主资料");
            if (!valid(entry.profile(), canonical.audit())) {
                results.add(conflict(entry, before, "正式字段容量、枚举、JSON或历史时间无效，禁止截断或补当前时间")); continue;
            }
            if (before != null && !Objects.equals(before.profile().username(), entry.profile().username())) {
                results.add(conflict(entry, before, "登录名仅大小写不同，须先裁决身份")); continue;
            }
            if (entry.targetId() != null && (before == null || !Objects.equals(entry.targetId(), before.id()))) {
                results.add(conflict(entry, before, "既有来源绑定不能重建已删除账号或改指其他账号")); continue;
            }
            var actors = new ActorDirectory(identities, targets, auditReferences);
            Audit audit = new Audit(actor(new ActorQuery(canonical.audit().creatorIdentity(), actors)), canonical.audit().createTime(),
                    actor(new ActorQuery(canonical.audit().updaterIdentity(), actors)), canonical.audit().updateTime());
            User after = new User(targets.get(username), canonical.profile(), audit);
            String action;
            if (before == null) {
                if (canonical.adjudicatedExistingId() != null) { results.add(conflict(entry, null, "裁决的现存账号缺失，不能重建")); continue; }
                action = "CREATED";
            } else if (canonical.targetId() == null) {
                if (!request.adoptExisting() || !Objects.equals(canonical.adjudicatedExistingId(), before.id())) {
                    results.add(conflict(entry, before, "现存同名账号必须有具体身份裁决和接管预览")); continue;
                }
                action = "ADOPTED";
            } else {
                if (canonical.lastApplied() == null || !Objects.equals(before, canonical.lastApplied())) {
                    results.add(conflict(entry, before, "正式账号业务字段在上次承接后变化，不能覆盖人工维护事实")); continue;
                }
                action = Objects.equals(before, after) ? "UNCHANGED" : "UPDATED";
            }
            results.add(new Result(entry.object(), entry.sourceKey(), before, after, action,
                    "承接已批准账号业务字段；凭据、登录事实和现有权限由原系统能力管理"));
        }
        for (AuditReference reference : request.auditReferences()) {
            User target = auditReferences.get(reference.identity());
            results.add(new Result(reference.object(), reference.sourceKey(), target, target, "REFERENCED",
                    "仅关联已裁决历史审计人；当前账号资料、凭据、角色和授权保持当前事实"));
        }
        // User adjudication permits shared contact values. They remain formal email/mobile facts.
        return List.copyOf(results);
    }
    private record ActorDirectory(Map<String, Entry> identities, Map<String, Long> targets, Map<String, User> references) {}
    private record ActorQuery(String key, ActorDirectory directory) {}
    private static String actor(ActorQuery query) {
        String key=query.key();var identities=query.directory().identities();
        var targets=query.directory().targets();var references=query.directory().references();
        if (key == null || key.isEmpty()) return key;
        if (references.containsKey(key)) return references.get(key).id().toString();
        Entry entry = identities.get(key);
        if (entry == null) throw new IllegalArgumentException("历史账号操作者未精确关联来源身份，须裁决，不能按名字猜配");
        return Objects.toString(targets.get(identity(entry.profile().username())), null);
    }
    private static boolean valid(Profile value, SourceAudit audit) {
        return required(value.username(), 30) && required(value.nickname(), 30) && optional(value.remark(), 500)
                && optional(value.email(), 50) && optional(value.mobile(), 11) && optional(value.avatar(), 512)
                && optional(value.employeeNo(), 50) && Set.of(0, 1).contains(value.status() == null ? -1 : value.status())
                && (value.sex() == null || Set.of(0, 1, 2).contains(value.sex()))
                && (value.customInfo() == null || value.customInfo().isObject() && value.customInfo().toString().length() <= 1024 * 1024)
                && audit.createTime() != null && audit.updateTime() != null;
    }
    private static boolean required(String value, int size) { return value != null && !value.isBlank() && value.length() <= size; }
    private static boolean optional(String value, int size) { return value == null || value.length() <= size; }
    private static String identity(String value) { return value == null ? null : value.toLowerCase(Locale.ROOT); }
    private static Set<Long> posts(Profile value) { return value.postIds() == null ? Set.of() : value.postIds(); }
    private static Result conflict(Entry entry, User before, String message) {
        return new Result(entry.object(), entry.sourceKey(), before, before, "CONFLICT", message);
    }
    private static User snapshot(UserRow value) {
        return new User(value.getId(), new Profile(value.getUsername(), value.getNickname(), value.getRemark(), value.getDeptId(),
                value.getPostIds(), value.getEmail(), value.getMobile(), value.getSex(), value.getAvatar(), value.getStatus(),
                value.getEmployeeNo(), value.getCustomInfo() == null ? null : JsonUtils.getObjectMapper().readTree(value.getCustomInfo())),
                new Audit(value.getCreator(), value.getCreateTime(), value.getUpdater(), value.getUpdateTime()));
    }
    private static UserRow entity(Long tenantId, User value) {
        Profile profile = value.profile();
        UserRow entity = UserRow.builder().id(value.id()).username(profile.username()).nickname(profile.nickname())
                .remark(profile.remark()).deptId(profile.departmentId()).postIds(profile.postIds()).email(profile.email())
                .mobile(profile.mobile()).sex(profile.sex()).avatar(profile.avatar()).status(profile.status()).employeeNo(profile.employeeNo())
                .customInfo(profile.customInfo() == null ? null : profile.customInfo().toString()).build();
        entity.setTenantId(tenantId); entity.setCreator(value.audit().creator()); entity.setCreateTime(value.audit().createTime());
        entity.setUpdater(value.audit().updater()); entity.setUpdateTime(value.audit().updateTime());
        return entity;
    }
    private static void requireTransaction() {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Direct account writes require the sync transaction");
    }
}
