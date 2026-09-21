package cn.iocoder.yudao.module.pms.project.controller.admin.projecttemplate;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.project.service.operation.ProjectBusinessResultSources;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

/** Authoring capabilities only; never reads business objects or grants execution permissions. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/project-templates/result-catalog")
public class ProjectBusinessResultCatalogController {
    private final ProjectBusinessResultSources sources;

    public record Source(String ownerContext, String entityType, String resultType, boolean currentLookup,
                         boolean exactLookup, boolean historicalLookup, boolean inventory, boolean changes,
                         boolean commitBarrier) { }

    @GetMapping
    @PreAuthorize("@ss.hasPermission('pms:project-template:update') or @ss.hasPermission('pms:project-plan:manage')")
    public CommonResult<List<Source>> list() {
        return CommonResult.success(sources.descriptors().stream().map(descriptor -> {
            var type = descriptor.type();
            return new Source(type.ownerContext(), type.entityType(), type.resultType(), descriptor.currentLookup(),
                    descriptor.exactLookup(), descriptor.historicalLookup(), sources.inventorySupported(type),
                    sources.changeSupported(type), sources.commitBarrierSupported(type));
        }).toList());
    }
}
