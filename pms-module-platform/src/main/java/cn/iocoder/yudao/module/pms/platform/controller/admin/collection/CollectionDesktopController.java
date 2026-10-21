package cn.iocoder.yudao.module.pms.platform.controller.admin.collection;

import cn.iocoder.yudao.framework.apilog.core.annotation.ApiAccessLog;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.pms.integration.api.deviceops.DeviceOpsDesktopApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.*;
import cn.iocoder.yudao.module.pms.platform.service.collection.CollectionDesktopApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pms/device-collection/sources/{entry}/{objectId}")
@ConditionalOnProperty(prefix="pms.integration.device-ops",name="enabled",havingValue="true")
public class CollectionDesktopController {
    private final CollectionDesktopApplicationService application;

    @PostMapping("/desktop-executions")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<CollectionDesktopApplicationService.Prepared> prepare(@PathVariable("entry") String entry,@PathVariable("objectId") Long objectId,
            @RequestBody Metadata input) {
        return success(application.prepare(new CollectionDesktopApplicationService.Preparation(source(entry,objectId),input.toRequest(),
                input.localConnectionId(),input.localConnectionVersion())));
    }

    @PostMapping("/desktop-access")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<DeviceOpsDesktopApi.Ticket> access(@PathVariable("entry") String entry,@PathVariable("objectId") Long objectId,@RequestBody Access input) {
        return success(application.access(new CollectionDesktopApplicationService.Access(source(entry,objectId),input.operation(),input.executionId())));
    }

    @PostMapping("/desktop-executions/{executionId}/resume")
    @ApiAccessLog(requestEnable=false,responseEnable=false)
    public CommonResult<CollectionDesktopApplicationService.Prepared> resume(@PathVariable("entry") String entry,@PathVariable("objectId") Long objectId,
            @PathVariable("executionId") Long executionId) {
        return success(application.resume(new CollectionDesktopApplicationService.Resume(source(entry,objectId),executionId)));
    }

    private CollectionApplicationApi.SourceQuery source(String entry,Long objectId) {
        var actor=SecurityFrameworkUtils.getLoginUserId();
        if(actor==null)throw new org.springframework.security.access.AccessDeniedException("请先登录");
        return new CollectionApplicationApi.SourceQuery(entry,objectId,actor);
    }

    public record Access(String operation,Long executionId){}
    public record Metadata(String requestKey,Long expectedVersion,Long deviceId,String host,Integer port,String protocol,
                           String username,String commands,Long templateId,Long retryOfId,String localConnectionId,Long localConnectionVersion) {
        @com.fasterxml.jackson.annotation.JsonAnySetter
        public void rejectUnknown(String name,Object value) { throw new IllegalArgumentException("本机采集不接受该字段"); }
        CollectionExecutionRequest toRequest() {
            var request=new CollectionExecutionRequest();
            request.setRequestKey(requestKey);request.setExpectedVersion(expectedVersion);request.setDeviceId(deviceId);
            request.setHost(host);request.setPort(port);request.setProtocol(protocol);request.setUsername(username);
            request.setCommands(commands);request.setTemplateId(templateId);request.setRetryOfId(retryOfId);
            return request;
        }
    }
}
