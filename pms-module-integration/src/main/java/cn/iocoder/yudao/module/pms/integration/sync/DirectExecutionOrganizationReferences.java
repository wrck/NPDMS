package cn.iocoder.yudao.module.pms.integration.sync;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.DirectExecutionOrderRow;
import cn.iocoder.yudao.module.system.api.organization.ManagedOrganizationApi;
import lombok.RequiredArgsConstructor;


import java.util.HashMap;
import java.util.Map;

/** Exact historical references; organization status is retained and grants are never changed. */
@RequiredArgsConstructor
final class DirectExecutionOrganizationReferences {
    private final ManagedOrganizationApi organizations;

    public Directory load() {
        long tenantId = TenantContextHolder.getRequiredTenantId();
        Map<String, ManagedOrganizationApi.Node> companies = new HashMap<>();
        Map<String, ManagedOrganizationApi.Node> departments = new HashMap<>();
        for (var node : organizations.list()) {
            var entity = node.company() != null ? node.company() : node.department();
            if (entity == null || !Long.valueOf(tenantId).equals(entity.getTenantId())
                    || Boolean.TRUE.equals(entity.getDeleted()) || node.id() == null || node.id() <= 0)
                throw new IllegalStateException("DPPMS_EXECUTION_ORGANIZATION_IDENTITY_INVALID");
            if (node.code() == null || node.code().isBlank()) continue;
            var index = node.company() != null ? companies : departments;
            if (index.putIfAbsent(node.code(), node) != null)
                throw new IllegalStateException("DPPMS_EXECUTION_ORGANIZATION_CODE_AMBIGUOUS");
        }
        return new Directory(companies, departments);
    }

    public record Directory(Map<String, ManagedOrganizationApi.Node> companies,
                            Map<String, ManagedOrganizationApi.Node> departments) {
        public Directory { companies = Map.copyOf(companies); departments = Map.copyOf(departments); }

        public void associate(DirectExecutionOrderRow incoming) {
            associate(new Association(incoming,Resolution.REQUIRED));
        }
        public void associate(Association command) {
            var incoming=command.incoming();
            if (incoming.getCompanyCode() != null && !incoming.getCompanyCode().isBlank()) {
                var company = companies.get(incoming.getCompanyCode());
                if (company == null && command.resolution()==Resolution.REQUIRED)
                    throw new IllegalArgumentException("DPPMS_EXECUTION_COMPANY_UNRESOLVED");
                if(company!=null) {
                    incoming.setCompanyId(company.id());
                    incoming.setCompanyName(company.name());
                }
            }
            if (incoming.getDepartmentCode() != null && !incoming.getDepartmentCode().isBlank()) {
                String code = incoming.getDepartmentCode();
                // Only the nine explicitly adjudicated aliases are converted. Never truncate or use a parent.
                if (code.matches("31120[2-9]|311210")) code = "1621" + code.substring(4);
                var department = departments.get(code);
                if (department == null && command.resolution()==Resolution.REQUIRED)
                    throw new IllegalArgumentException("DPPMS_EXECUTION_DEPARTMENT_UNRESOLVED");
                incoming.setDepartmentCode(code);
                if(department!=null)incoming.setDepartmentId(department.id());
                // department_name carries the original officeName; a current name does not replace it.
            }
        }
    }
    public enum Resolution { REQUIRED, AVAILABLE }
    public record Association(DirectExecutionOrderRow incoming,Resolution resolution) {}
}
