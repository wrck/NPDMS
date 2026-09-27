package cn.iocoder.yudao.module.pms.platform.controller.admin.collection;
import cn.iocoder.yudao.framework.common.pojo.*;
import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.service.collection.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.LocalDateTime;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
@RestController @RequestMapping("/api/v1/pms/device-collection") @RequiredArgsConstructor
public class CollectionApplicationController {
    private final CollectionApplicationApi application;
    private final CollectionTemplateService templates;
    private final CollectionConnectionService connections;
    @GetMapping("/sources/{entry}/{objectId}") public CommonResult<CollectionSourceAdapter.Source> context(@PathVariable String entry,@PathVariable Long objectId){return success(application.context(entry,objectId,actor()));}
    @PostMapping("/sources/{entry}/{objectId}/executions") @ApiAccessLog(requestEnable=false)
    public CommonResult<CollectionApplicationApi.Execution> submit(@PathVariable String entry,@PathVariable Long objectId,@RequestBody CollectionExecutionRequest request){return success(application.submit(entry,objectId,actor(),request));}
    @GetMapping("/sources/{entry}/{objectId}/executions") public CommonResult<PageResult<CollectionApplicationApi.Execution>> page(@PathVariable String entry,@PathVariable Long objectId,@RequestParam(defaultValue="1") int pageNo,@RequestParam(defaultValue="10") int pageSize){return success(application.page(entry,objectId,actor(),pageNo,pageSize));}
    @GetMapping("/sources/{entry}/{objectId}/executions/by-request-key") public CommonResult<CollectionApplicationApi.Execution> find(@PathVariable String entry,@PathVariable Long objectId,@RequestParam String requestKey){return success(application.findByRequestKey(entry,objectId,actor(),requestKey));}
    @PostMapping("/sources/{entry}/{objectId}/executions/{id}/consume") public CommonResult<CollectionApplicationApi.Execution> consume(@PathVariable String entry,@PathVariable Long objectId,@PathVariable Long id){return success(application.consume(entry,objectId,actor(),id));}
    @PostMapping("/sources/{entry}/{objectId}/executions/{id}/cancel") public CommonResult<Boolean> cancel(@PathVariable String entry,@PathVariable Long objectId,@PathVariable Long id){application.cancel(entry,objectId,actor(),id);return success(true);}
    @PostMapping("/sources/{entry}/{objectId}/executions/{id}/download") public CommonResult<String> download(@PathVariable String entry,@PathVariable Long objectId,@PathVariable Long id){return success(application.download(entry,objectId,actor(),id));}
    @GetMapping("/templates") public CommonResult<List<CollectionTemplateService.View>> templates(@RequestParam(required=false) String purpose,@RequestParam(required=false) String protocol,@RequestParam(defaultValue="false") boolean publishedOnly){return success(templates.list(actor(),purpose,protocol,publishedOnly));}
    @PostMapping("/templates") public CommonResult<CollectionTemplateService.View> saveTemplate(@RequestBody CollectionTemplateService.Draft request){return success(templates.save(actor(),request));}
    @PostMapping("/templates/{id}/publish") public CommonResult<CollectionTemplateService.View> publish(@PathVariable Long id,@RequestParam Long version){return success(templates.publish(actor(),id,version));}
    @PostMapping("/templates/{id}/retire") public CommonResult<CollectionTemplateService.View> retire(@PathVariable Long id,@RequestParam Long version){return success(templates.retire(actor(),id,version));}
    @GetMapping("/connections") public CommonResult<List<CollectionConnectionService.View>> connections(@RequestParam Long projectId){return success(connections.owned(actor(),projectId));}
    @PostMapping("/connections") @ApiAccessLog(requestEnable=false)
    public CommonResult<CollectionConnectionService.View> saveConnection(@RequestBody CollectionConnectionService.Save request){return success(connections.save(actor(),request));}
    @GetMapping("/connections/usable") public CommonResult<List<CollectionConnectionService.View>> usable(@RequestParam Long projectId,@RequestParam(required=false) Long deviceId,@RequestParam String protocol,@RequestParam(required=false) Long templateId){return success(connections.usable(actor(),projectId,deviceId,protocol,templateId));}
    @GetMapping("/connections/{id}/grants") public CommonResult<List<CollectionConnectionService.GrantView>> grants(@PathVariable Long id){return success(connections.grants(actor(),id));}
    @PostMapping("/connections/{id}/grants") public CommonResult<CollectionConnectionService.GrantView> grant(@PathVariable Long id,@RequestBody Grant request){return success(connections.grant(actor(),id,request.userId(),request.templateId(),request.expiresAt()));}
    @PostMapping("/connections/{id}/grants/{grantId}/revoke") public CommonResult<Boolean> revoke(@PathVariable Long id,@PathVariable Long grantId){connections.revoke(actor(),id,grantId);return success(true);}
    @PostMapping("/connections/{id}/disable") public CommonResult<Boolean> disable(@PathVariable Long id){connections.disable(actor(),id);return success(true);}
    private Long actor(){Long id=SecurityFrameworkUtils.getLoginUserId();if(id==null)throw new org.springframework.security.access.AccessDeniedException("请先登录");return id;}
    public record Grant(Long userId,Long templateId,LocalDateTime expiresAt){}
}
