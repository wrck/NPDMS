package cn.iocoder.yudao.module.pms.project.api.scope;

import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectAllScopeQuery;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PROJ维护的关联只读契约，不向消费者展开项目ID。
 * 配套 mapper/projectscope/ProjectViewReadScopeSql.xml 只返回完整可见项目，
 * 消费者必须使用Owner生成的范围，不能自行构造管理员身份或复制授权SQL。
 */
public interface ProjectViewReadScopeApi {
    ReadScope prepare(ProjectAllScopeQuery query);

    enum Mode { TENANT_ADMIN, TREE_ADMIN, SCOPED }

    record Tree(Long rootProjectId, Long treeVersion) {}
    record Organization(Long companyId, Long departmentId) {}

    record ReadScope(Long tenantId, Long subjectUserId, Mode mode,
                     LocalDateTime effectiveAt, List<Tree> trees, List<Organization> organizations, Long officeReadonlyProjectId) {
        public ReadScope {
            trees = List.copyOf(trees);
            organizations = List.copyOf(organizations);
        }
        public ReadScope(Long tenantId, Long subjectUserId, Mode mode, LocalDateTime effectiveAt, List<Tree> trees) {
            this(tenantId, subjectUserId, mode, effectiveAt, trees, List.of(), null);
        }
        public ReadScope(Long tenantId, Long subjectUserId, Mode mode, LocalDateTime effectiveAt, List<Tree> trees, List<Organization> organizations) {
            this(tenantId, subjectUserId, mode, effectiveAt, trees, organizations, null);
        }
        public String creator() { return String.valueOf(subjectUserId); }
    }
}
