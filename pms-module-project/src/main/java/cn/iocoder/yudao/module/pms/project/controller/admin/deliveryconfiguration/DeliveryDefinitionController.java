package cn.iocoder.yudao.module.pms.project.controller.admin.deliveryconfiguration;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.module.pms.project.domain.template.DeliveryDefinitionKind;
import cn.iocoder.yudao.module.pms.project.dal.mysql.deliveryconfiguration.query.DeliveryDefinitionPageQuery;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.*;
import cn.iocoder.yudao.module.pms.project.service.deliveryconfiguration.DeliveryDefinitionModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
/** PM-03 / F-PROJ-009: exact definition revision REST. */
@RestController @RequiredArgsConstructor
@RequestMapping("/api/v1/pms/delivery-definitions")
public class DeliveryDefinitionController {
    private final DeliveryDefinitionService service;
    @GetMapping({"", "/page"})
    @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<PageResult<Revision>> page(@RequestParam(defaultValue="1") Integer pageNo,
            @RequestParam(defaultValue="20") Integer pageSize, @RequestParam(required=false) DeliveryDefinitionKind definitionKind,
            @RequestParam(required=false) String definitionCode, @RequestParam(required=false) String revisionState) {
        DeliveryDefinitionPageQuery query = new DeliveryDefinitionPageQuery(); query.setPageNo(pageNo); query.setPageSize(pageSize);
        query.setDefinitionKind(definitionKind == null ? null : definitionKind.name());
        query.setDefinitionCode(definitionCode); query.setRevisionState(revisionState); return success(service.page(query));
    }
    @GetMapping("/{id}") @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<Revision> get(@PathVariable Long id) { return success(service.get(id)); }
    @PostMapping @PreAuthorize("@ss.hasPermission('pms:project-template:update')")
    public CommonResult<Long> create(@RequestBody Save body, @RequestHeader("Idempotency-Key") String key) { return success(service.create(body,key)); }
    @PutMapping("/{id}") @PreAuthorize("@ss.hasPermission('pms:project-template:update')")
    public CommonResult<Long> update(@PathVariable Long id, @RequestBody Save body,
            @RequestHeader("If-Match") String version, @RequestHeader("Idempotency-Key") String key) {
        return success(service.update(id, DeliveryConfigurationCommands.version(version), body, key));
    }
    @PostMapping("/{id}/actions/copy") @PreAuthorize("@ss.hasPermission('pms:project-template:update')")
    public CommonResult<Long> copy(@PathVariable Long id, @RequestHeader("If-Match") String version,
            @RequestHeader("Idempotency-Key") String key) { return success(service.copy(id,DeliveryConfigurationCommands.version(version),key)); }
    @PostMapping("/{id}/actions/validate") @PreAuthorize("@ss.hasPermission('pms:project-template:query')")
    public CommonResult<Validation> validate(@PathVariable Long id) { return success(service.validate(id)); }
    @PostMapping("/{id}/actions/publish") @PreAuthorize("@ss.hasPermission('pms:project-template:publish')")
    public CommonResult<Long> publish(@PathVariable Long id, @RequestHeader("If-Match") String version,
            @RequestHeader("Idempotency-Key") String key) { return success(service.publish(id,DeliveryConfigurationCommands.version(version),key)); }
    @PostMapping("/{id}/actions/disable") @PreAuthorize("@ss.hasPermission('pms:project-template:disable')")
    public CommonResult<Long> disable(@PathVariable Long id, @RequestHeader("If-Match") String version,
            @RequestHeader("Idempotency-Key") String key) { return success(service.disable(id,DeliveryConfigurationCommands.version(version),key)); }
}
