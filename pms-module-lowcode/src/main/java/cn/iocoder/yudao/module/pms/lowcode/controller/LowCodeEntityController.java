package cn.iocoder.yudao.module.pms.lowcode.controller;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.module.pms.lowcode.dto.DdlResultDTO;
import cn.iocoder.yudao.module.pms.lowcode.dto.EntityDesignDTO;
import cn.iocoder.yudao.module.pms.lowcode.engine.ddl.DdlBackup;
import cn.iocoder.yudao.module.pms.lowcode.engine.ddl.DdlExecutionService;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeEntity;
import cn.iocoder.yudao.module.pms.lowcode.entity.LowCodeRelation;
import cn.iocoder.yudao.module.pms.lowcode.service.LowCodeEntityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 低代码实体设计器 Controller。
 *
 * <p>提供实体/字段/关联的 CRUD、DDL 生成、发布等接口。</p>
 */
@Tag(name = "低代码实体设计器", description = "LowCode entity designer APIs")
@RestController
@RequestMapping("/api/lowcode/entity")
@RequiredArgsConstructor
public class LowCodeEntityController {

    private final LowCodeEntityService entityService;
    private final DdlExecutionService ddlExecutionService;

    @Operation(summary = "查询实体列表")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermission('lowcode:entity:list')")
    public CommonResult<List<LowCodeEntity>> list() {
        return CommonResult.success(entityService.list());
    }

    @Operation(summary = "查询完整实体设计")
    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:entity:query')")
    public CommonResult<EntityDesignDTO> getDesign(@PathVariable Long id) {
        return CommonResult.success(entityService.getDesign(id));
    }

    @Operation(summary = "保存实体设计（实体+字段+关联）")
    @PostMapping
    @PreAuthorize("@ss.hasPermission('lowcode:entity:add')")
    public CommonResult<LowCodeEntity> saveDesign(@Valid @RequestBody EntityDesignDTO design) {
        return CommonResult.success(entityService.saveDesign(design));
    }

    @Operation(summary = "生成 DDL 语句")
    @GetMapping("/{id}/ddl")
    @PreAuthorize("@ss.hasPermission('lowcode:entity:ddl')")
    public CommonResult<DdlResultDTO> generateDdl(@PathVariable Long id) {
        return CommonResult.success(entityService.generateDdl(id));
    }

    @Operation(summary = "发布实体")
    @PostMapping("/{id}/publish")
    @PreAuthorize("@ss.hasPermission('lowcode:entity:publish')")
    public CommonResult<LowCodeEntity> publish(@PathVariable Long id,
                                          @RequestParam(required = false) String changeLog) {
        return CommonResult.success(entityService.publish(id, changeLog));
    }

    @Operation(summary = "删除实体")
    @DeleteMapping("/{id}")
    @PreAuthorize("@ss.hasPermission('lowcode:entity:delete')")
    public CommonResult<Void> delete(@PathVariable Long id) {
        entityService.removeById(id);
        return CommonResult.success(null);
    }

    @PostMapping("/{entityId}/relations")
    @Operation(summary = "保存实体关联", description = "保存实体的关联关系（先删后插）")
    @PreAuthorize("@ss.hasPermission('lowcode:entity:edit')")
    public CommonResult<List<LowCodeRelation>> saveRelations(
            @PathVariable Long entityId,
            @Valid @RequestBody List<LowCodeRelation> relations) {
        return CommonResult.success(entityService.saveRelations(entityId, relations));
    }

    @Operation(summary = "校验物理表名唯一性")
    @GetMapping("/check-table-name")
    @PreAuthorize("@ss.hasPermission('lowcode:entity:list')")
    public CommonResult<Boolean> checkTableName(@RequestParam String tableName,
                                           @RequestParam(required = false) Long excludeId) {
        return CommonResult.success(entityService.isTableNameExists(tableName, excludeId));
    }

    @Operation(summary = "查询实体 DDL 备份记录列表")
    @GetMapping("/{entityId}/ddl-backups")
    @PreAuthorize("@ss.hasPermission('lowcode:entity:ddl')")
    public CommonResult<List<DdlBackup>> listDdlBackups(@PathVariable Long entityId) {
        return CommonResult.success(ddlExecutionService.listBackups(entityId));
    }

    @Operation(summary = "回滚最近一次 DDL 操作")
    @PostMapping("/{entityId}/rollback-ddl")
    @PreAuthorize("@ss.hasPermission('lowcode:entity:ddl')")
    public CommonResult<String> rollbackLastDdl(@PathVariable Long entityId) {
        return CommonResult.success(ddlExecutionService.rollbackLastDdl(entityId));
    }

    @Operation(summary = "按备份记录 ID 回滚 DDL")
    @PostMapping("/ddl/rollback/{backupId}")
    @PreAuthorize("@ss.hasPermission('lowcode:entity:ddl')")
    public CommonResult<Void> rollbackByBackupId(@PathVariable Long backupId) {
        ddlExecutionService.rollbackByBackupId(backupId);
        return CommonResult.success(null);
    }
}
