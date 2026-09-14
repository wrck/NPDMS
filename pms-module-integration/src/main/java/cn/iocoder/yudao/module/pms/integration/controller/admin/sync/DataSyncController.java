package cn.iocoder.yudao.module.pms.integration.controller.admin.sync;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.infra.api.db.ExternalDataSourceApi;
import cn.iocoder.yudao.module.pms.integration.api.sync.DataSyncAdapter;
import cn.iocoder.yudao.module.pms.integration.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.mysql.sync.*;
import cn.iocoder.yudao.module.pms.integration.dal.dataobject.sync.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.sql.SQLException;
import java.util.List;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequestMapping("/api/v1/pms/integration")
@RequiredArgsConstructor
public class DataSyncController {
    private final SyncConnectionService connections;
    private final ExternalDataSourceApi sources;
    private final MysqlSyncReader reader;
    private final SyncDefinitionValidator definitions;
    private final SyncTaskService tasks;
    private final SyncRunService runner;
    private final SyncRunMapper runs;
    private final SyncBindingMapper bindings;
    private final cn.iocoder.yudao.module.system.api.permission.PermissionApi permissions;
    public record Schedule(Integer version,boolean enabled) {}

    @GetMapping("/connections")
    @PreAuthorize("@ss.hasPermission('pms:integration:connection')")
    public CommonResult<PageResult<SyncConnectionService.Info>> connections(@Valid SyncQueries.Page q){return success(connections.page(q));}

    @PostMapping("/connections")
    @PreAuthorize("@ss.hasPermission('pms:integration:connection')")
    @cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog(requestEnable=false,sanitizeKeys={"password"})
    public CommonResult<Long> saveConnection(@RequestBody SyncConnectionService.Save request){
        String permission=request.id()!=null?"infra:data-source-config:update":
                request.dataSourceId()!=null?"infra:data-source-config:query":"infra:data-source-config:create";
        if(!permissions.hasAnyPermissions(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),permission))
            throw new org.springframework.security.access.AccessDeniedException("缺少数据源管理权限");
        return success(connections.save(request));
    }
    @GetMapping("/data-sources")
    @PreAuthorize("@ss.hasPermission('pms:integration:connection') && @ss.hasPermission('infra:data-source-config:query')")
    public CommonResult<List<ExternalDataSourceApi.Info>> dataSources(){return success(sources.list());}

    @PostMapping("/connections/{id}/test")
    @PreAuthorize("@ss.hasPermission('pms:integration:connection')")
    public CommonResult<Boolean> testConnection(@PathVariable Long id)throws SQLException{
        try(var c=sources.openReadOnly(connections.required(id).getDataSourceId())){return success(c.isValid(10));}
    }
    @GetMapping("/connections/{id}/tables")
    @PreAuthorize("@ss.hasPermission('pms:integration:configure')")
    public CommonResult<List<MysqlSyncReader.Table>> tables(@PathVariable Long id)throws SQLException{
        return success(reader.tables(connections.required(id).getDataSourceId()));
    }
    @GetMapping("/connections/{id}/columns")
    @PreAuthorize("@ss.hasPermission('pms:integration:configure')")
    public CommonResult<List<MysqlSyncReader.Column>> columns(@PathVariable Long id,@RequestParam String table)throws SQLException{
        return success(reader.columns(connections.required(id).getDataSourceId(),table));
    }
    @GetMapping("/adapters")
    @PreAuthorize("@ss.hasPermission('pms:integration:view')")
    public CommonResult<List<DataSyncAdapter.Descriptor>> adapters(){return success(definitions.descriptors());}
    @GetMapping("/templates/ehr")
    @PreAuthorize("@ss.hasPermission('pms:integration:configure')")
    public CommonResult<SyncDefinition> template(@RequestParam Long connectionId){
        connections.required(connectionId);return success(EhrSyncTemplate.create(connectionId));
    }
    @GetMapping("/tasks")
    @PreAuthorize("@ss.hasPermission('pms:integration:view')")
    public CommonResult<PageResult<SyncTaskService.View>> tasks(@Valid SyncQueries.Page q){return success(tasks.page(q));}
    @GetMapping("/templates/dppms-orders")
    @PreAuthorize("@ss.hasPermission('pms:integration:configure')")
    public CommonResult<SyncDefinition> orderTemplate(@RequestParam Long connectionId){
        connections.required(connectionId);return success(DppmsOrderSyncTemplate.create(connectionId));
    }
    @GetMapping("/tasks/{id}")
    @PreAuthorize("@ss.hasPermission('pms:integration:view')")
    public CommonResult<SyncTaskService.View> task(@PathVariable Long id){return success(tasks.get(id));}
    @PostMapping("/tasks")
    @PreAuthorize("@ss.hasPermission('pms:integration:configure')")
    public CommonResult<Long> saveTask(@RequestBody SyncTaskService.Save request) {
        if(request.definition()!=null && request.definition().sources()!=null
                && request.definition().sources().stream().anyMatch(s->"SQL".equals(s.readMode())))
            requireSqlPermission();
        // SQL permissions are also required to replace an existing SQL definition.
        if(request.id()!=null && tasks.get(request.id()).definition().sources().stream().anyMatch(s->"SQL".equals(s.readMode())))
            requireSqlPermission();
        return success(tasks.save(request));
    }
    @PostMapping("/tasks/configuration-check")
    @PreAuthorize("@ss.hasPermission('pms:integration:configure')")
    public CommonResult<SyncTaskService.ConfigurationCheck> checkConfiguration(@RequestBody SyncTaskService.Save request) {
        return success(tasks.checkConfiguration(request));
    }
    @PutMapping("/tasks/{id}/schedule")
    @PreAuthorize("@ss.hasPermission('pms:integration:schedule')")
    public CommonResult<Boolean> schedule(@PathVariable Long id,@RequestBody Schedule request){
        tasks.schedule(id,request.version(),request.enabled());return success(true);
    }
    @PostMapping("/runs")
    @PreAuthorize("@ss.hasPermission('pms:integration:execute')")
    public CommonResult<Long> run(@RequestBody SyncRunService.Start request){
        if((request.adoptExisting()||request.confirmPreparation())&&!permissions.hasAnyPermissions(
                cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),"pms:integration:adopt"))
            throw new org.springframework.security.access.AccessDeniedException("缺少受控接管权限");
        return success(runner.start(request));
    }
    @GetMapping("/runs")
    @PreAuthorize("@ss.hasPermission('pms:integration:view')")
    public CommonResult<PageResult<SyncRunDO>> runs(@Valid SyncQueries.Page q){
        q.setTenantId(SyncTaskService.tenant());var p=runs.selectPage(q);
        return success(p);
    }
    @GetMapping("/runs/{id}")
    @PreAuthorize("@ss.hasPermission('pms:integration:view')")
    public CommonResult<SyncRunDO> run(@PathVariable Long id){
        var r=runner.required(id);r.setResultJson(null);r.setEvidenceJson(null);return success(r);
    }
    @GetMapping("/runs/{id}/changes")
    @PreAuthorize("@ss.hasPermission('pms:integration:view')")
    public CommonResult<PageResult<DataSyncAdapter.Change>> changes(@PathVariable Long id,@Valid SyncQueries.Page q){
        var r=runner.required(id);var all=r.getResultJson()==null?List.<DataSyncAdapter.Change>of():
                JsonUtils.parseArray(r.getResultJson(),DataSyncAdapter.Change.class);
        int start=Math.min((q.getPageNo()-1)*q.getPageSize(),all.size()),end=Math.min(start+q.getPageSize(),all.size());
        return success(new PageResult<>(all.subList(start,end),(long)all.size()));
    }
    @GetMapping("/mappings")
    @PreAuthorize("@ss.hasPermission('pms:integration:view')")
    public CommonResult<PageResult<SyncBindingDO>> mappings(@Valid SyncQueries.Page q){
        tasks.required(q.getTaskId());q.setTenantId(SyncTaskService.tenant());return success(bindings.selectPage(q));
    }
    private void requireSqlPermission(){
        if(!permissions.hasAnyPermissions(cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId(),
                "pms:integration:sql"))throw new org.springframework.security.access.AccessDeniedException("缺少只读 SQL 配置权限");
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public CommonResult<Void> invalidRequest(IllegalArgumentException exception) {
        return CommonResult.error(400,exception.getMessage());
    }
    @ExceptionHandler(SQLException.class)
    public CommonResult<Void> sourceUnavailable(SQLException exception) {
        return CommonResult.error(503,"来源连接或查询失败，请检查配置与访问权限");
    }
    @ExceptionHandler(SyncTaskConflictException.class)
    public CommonResult<Void> configurationConflict(SyncTaskConflictException exception) {
        return CommonResult.error(409,exception.getMessage());
    }
    @ExceptionHandler(org.springframework.dao.DataAccessException.class)
    public CommonResult<Void> localDatabaseFailure(org.springframework.dao.DataAccessException exception) {
        if(exception instanceof org.springframework.dao.DuplicateKeyException)
            return CommonResult.error(409,"集成配置唯一键冲突，请刷新列表并检查已有配置");
        return CommonResult.error(500,"本地集成数据保存或查询失败，请联系管理员检查；并非来源连接失败");
    }
}
