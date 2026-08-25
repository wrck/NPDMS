package cn.iocoder.yudao.module.pms.project.service.projecttree;

import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectScopeQuery;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projectmanual.ProjectMasterDO;
import cn.iocoder.yudao.module.pms.project.dal.dataobject.projecttree.ProjectTreeVersionDO;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projectmanual.ProjectMasterMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreePathMapper;
import cn.iocoder.yudao.module.pms.project.dal.mysql.projecttree.ProjectTreeVersionMapper;
import cn.iocoder.yudao.module.pms.project.service.projecttree.command.ProjectTreeQuery;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeScopeService;
import cn.iocoder.yudao.module.pms.project.service.projectscope.ProjectTreeViewSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_PROJECTION_UNAVAILABLE;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_QUERY_INVALID;
import static cn.iocoder.yudao.module.pms.project.enums.ErrorCodeConstants.PROJECT_TREE_SCOPE_FORBIDDEN;
import static cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi.ACTION_VIEW;

@Service
@RequiredArgsConstructor
public class ProjectTreeQueryService {
    private static final int DEFAULT_PAGE_SIZE = 100;
    private static final int MAX_PAGE_SIZE = 500;

    private final ProjectMasterMapper projectMapper;
    private final ProjectTreeVersionMapper versionMapper;
    private final ProjectTreePathMapper pathMapper;
    private final ProjectTreeMetrics metrics;
    private final ProjectTreeScopeService scopeService;
    private final ProjectTreeViewSanitizer viewSanitizer;

    public ProjectTreeQueryResult query(ProjectTreeQuery query, Actor actor) {
        validate(query, actor);
        long started = System.nanoTime();
        ProjectMasterDO anchor = projectMapper.selectById(query.anchorProjectId());
        if (anchor == null || !Objects.equals(anchor.getTenantId(), actor.tenantId())) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        Cursor cursor = decodeCursor(query.cursor());
        validateCursor(cursor, query);
        Long rootId = cursor == null ? (anchor.getRootId() == null ? anchor.getId() : anchor.getRootId())
                : cursor.rootProjectId();
        ProjectTreeVersionDO active = cursor == null
                ? versionMapper.selectLatestActive(rootId)
                : versionMapper.selectActiveVersion(rootId, cursor.treeVersion());
        if (active == null) {
            throw exception(PROJECT_TREE_PROJECTION_UNAVAILABLE);
        }
        ProjectTreeVersionDO latest = versionMapper.selectLatest(rootId);
        boolean updating = latest != null && "BUILDING".equals(latest.getStatus())
                && latest.getTreeVersion() > active.getTreeVersion();
        int offset = cursor == null ? 0 : cursor.offset();
        if (offset < 0) {
            throw exception(PROJECT_TREE_QUERY_INVALID);
        }
        int pageSize = query.pageSize() == null ? DEFAULT_PAGE_SIZE
                : Math.min(Math.max(query.pageSize(), 1), MAX_PAGE_SIZE);
        ProjectTreeScopeService.ProjectTreeScope scope = scopeService.resolve(new ProjectScopeQuery(
                actor.tenantId(), actor.actorId(), query.anchorProjectId(), ACTION_VIEW, active.getTreeVersion()));
        if (scope.rootProjectId() != rootId
                || scope.visibility(query.anchorProjectId()) == ProjectTreeScopeService.Visibility.NONE) {
            throw exception(PROJECT_TREE_SCOPE_FORBIDDEN);
        }
        List<ProjectMasterDO> fetched = resolvePage(query, actor.tenantId(), rootId,
                active.getTreeVersion(), scope.visibleProjectIds(), offset, pageSize + 1);
        boolean hasNext = fetched.size() > pageSize;
        List<ProjectMasterDO> page = hasNext ? List.copyOf(fetched.subList(0, pageSize)) : List.copyOf(fetched);
        List<ProjectTreeViewSanitizer.ProjectTreeNodeView> visiblePage = page.stream()
                .map(project -> viewSanitizer.sanitize(project, scope.visibility(project.getId())))
                .filter(Objects::nonNull).toList();
        String next = hasNext ? encodeCursor(rootId, active.getTreeVersion(), query, offset + pageSize) : null;
        metrics.query(query.queryType().name(), updating, System.nanoTime() - started, visiblePage.size());
        return new ProjectTreeQueryResult(active.getTreeVersion(), visiblePage, next, updating);
    }

    private List<ProjectMasterDO> resolvePage(ProjectTreeQuery query, Long tenantId, Long rootId,
                                              Long treeVersion, java.util.Set<Long> visibleProjectIds,
                                              int offset, int limit) {
        return switch (query.queryType()) {
            case CHILDREN -> pathMapper.selectDescendantsPage(tenantId, rootId, treeVersion,
                    query.anchorProjectId(), true, visibleProjectIds, offset, limit);
            case DESCENDANTS -> pathMapper.selectDescendantsPage(tenantId, rootId, treeVersion,
                    query.anchorProjectId(), false, visibleProjectIds, offset, limit);
            case ANCESTORS -> pathMapper.selectPathPage(tenantId, rootId, treeVersion,
                    query.anchorProjectId(), false, visibleProjectIds, offset, limit);
            case LOCATE -> pathMapper.selectPathPage(tenantId, rootId, treeVersion,
                    query.anchorProjectId(), true, visibleProjectIds, offset, limit);
            case BUSINESS_LEVEL -> pathMapper.selectBusinessLevelPage(tenantId, rootId, treeVersion,
                    query.businessLevelCode(), visibleProjectIds, offset, limit);
        };
    }

    private void validate(ProjectTreeQuery query, Actor actor) {
        if (query == null || query.anchorProjectId() == null || query.queryType() == null
                || actor == null || actor.tenantId() == null || actor.actorId() == null
                || (query.queryType() == ProjectTreeQuery.QueryType.BUSINESS_LEVEL
                && (query.businessLevelCode() == null || query.businessLevelCode().isBlank()))) {
            throw exception(PROJECT_TREE_QUERY_INVALID);
        }
    }

    private String encodeCursor(long rootId, long treeVersion, ProjectTreeQuery query, int offset) {
        String level = query.businessLevelCode() == null ? "" : Base64.getUrlEncoder().withoutPadding()
                .encodeToString(query.businessLevelCode().getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                (rootId + ":" + treeVersion + ":" + query.anchorProjectId() + ":"
                        + query.queryType().name() + ":" + offset + ":" + level)
                        .getBytes(StandardCharsets.UTF_8));
    }

    private Cursor decodeCursor(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", -1);
            if (parts.length != 6) throw new IllegalArgumentException();
            String level = parts[5].isEmpty() ? null : new String(
                    Base64.getUrlDecoder().decode(parts[5]), StandardCharsets.UTF_8);
            return new Cursor(Long.parseLong(parts[0]), Long.parseLong(parts[1]),
                    Long.parseLong(parts[2]), ProjectTreeQuery.QueryType.valueOf(parts[3]),
                    Integer.parseInt(parts[4]), level);
        } catch (RuntimeException failure) {
            throw exception(PROJECT_TREE_QUERY_INVALID);
        }
    }

    private void validateCursor(Cursor cursor, ProjectTreeQuery query) {
        if (cursor != null && (!Objects.equals(cursor.anchorProjectId(), query.anchorProjectId())
                || cursor.queryType() != query.queryType()
                || !Objects.equals(cursor.businessLevelCode(), query.businessLevelCode()))) {
            throw exception(PROJECT_TREE_QUERY_INVALID);
        }
    }

    public record Actor(Long tenantId, Long actorId) {
    }

    public record ProjectTreeQueryResult(Long treeVersion,
                                         List<ProjectTreeViewSanitizer.ProjectTreeNodeView> items,
                                         String nextCursor, boolean updating) {
    }

    private record Cursor(long rootProjectId, long treeVersion, long anchorProjectId,
                          ProjectTreeQuery.QueryType queryType, int offset, String businessLevelCode) {
    }
}
