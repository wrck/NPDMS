package cn.iocoder.yudao.module.pms.asset.service.device;

import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.ProjectSerialExportMapper;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.projection.ProjectSerialExportRow;
import cn.iocoder.yudao.module.pms.asset.dal.mysql.device.query.ProjectSerialExportQuery;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.project.api.scope.ProjectScopeApi;
import cn.iocoder.yudao.module.pms.project.api.scope.dto.ProjectCurrentScopeQuery;
import cn.iocoder.yudao.module.system.api.permission.PermissionApi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import static cn.iocoder.yudao.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN;
import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;

@Service
@RequiredArgsConstructor
public class ProjectSerialExportService {
    private final ProjectSerialExportMapper mapper;
    private final ProjectScopeApi projects;
    private final PermissionApi permissions;
    public record Request(Long projectId, String sn, String name, String productCode) { }

    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<ProjectSerialExportRow> read(Request request) {
        var user=SecurityFrameworkUtils.getLoginUser();
        Long tenant=TenantContextHolder.getTenantId();
        if (request==null || request.projectId()==null || request.projectId()<=0 || user==null || tenant==null
                || !tenant.equals(user.getTenantId())
                || !permissions.hasAnyPermissions(user.getId(),"pms:device:query")
                || !permissions.hasAnyPermissions(user.getId(),"pms:project-device:export")) throw exception(FORBIDDEN);
        var scope=projects.resolveCurrent(new ProjectCurrentScopeQuery(tenant,user.getId(),request.projectId(),ProjectScopeApi.ACTION_VIEW));
        if (scope==null || scope.fullProjectIds()==null || !scope.fullProjectIds().contains(request.projectId())) throw exception(FORBIDDEN);
        var query=ProjectSerialExportQuery.builder().tenantId(tenant).projectId(request.projectId())
                .sn(trim(request.sn())).name(trim(request.name())).productCode(request.productCode()).limit(500).build();
        List<ProjectSerialExportRow> result=new ArrayList<>();
        while (true) {
            var batch=Objects.requireNonNull(mapper.selectBatch(query));
            batch.forEach(row->row.setLocation(locationText(row.getLocationSnapshot())));
            result.addAll(batch);
            if (batch.size()<query.limit()) return result;
            query=query.toBuilder().beforeId(batch.getLast().getDeviceId()).build();
        }
    }

    private static String trim(String value) { return value==null ? null : value.trim(); }

    private static String locationText(String snapshot) {
        if (snapshot==null || snapshot.isEmpty()) return null;
        try {
            var value=JsonUtils.parseTree(snapshot);
            if (value.isTextual()) return value.asText();
            var text=value.isObject() ? value.get("fallbackLocation") : null;
            return text!=null && text.isTextual() ? text.asText() : null;
        } catch (RuntimeException invalidJson) { return snapshot; }
    }
}
