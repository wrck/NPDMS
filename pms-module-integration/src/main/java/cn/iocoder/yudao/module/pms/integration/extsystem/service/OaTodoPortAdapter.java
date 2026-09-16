package cn.iocoder.yudao.module.pms.integration.extsystem.service;

import cn.iocoder.yudao.module.pms.integration.extsystem.model.oa.OaTodoRequest;
import cn.iocoder.yudao.module.pms.platform.api.spi.OaTodoPort;
import cn.iocoder.yudao.module.pms.platform.api.spi.dto.OaTodoCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * OA 待办端口适配器。
 *
 * <p>实现 platform-api 声明的稳定跨模块端口，并委托既有 {@link OaIntegrationService}
 * 完成实际 OA 调用。字段转换保持 OaTodoCommand → OaTodoRequest 一一映射，旧 OA
 * 集成功能与接口不变。</p>
 */
@Component
@RequiredArgsConstructor
public class OaTodoPortAdapter implements OaTodoPort {

    private final OaIntegrationService oaIntegrationService;

    @Override
    public void pushTodo(OaTodoCommand command) {
        OaTodoRequest request = OaTodoRequest.builder()
                .title(command.getTitle())
                .content(command.getContent())
                .handlerUserId(command.getHandlerUserId())
                .processInstanceId(command.getProcessInstanceId())
                .businessKey(command.getBusinessKey())
                .processUrl(command.getProcessUrl())
                .businessType(command.getBusinessType())
                .build();
        oaIntegrationService.pushTodo(request);
    }

    @Override
    public void completeTodo(String businessKey) {
        oaIntegrationService.completeTodo(businessKey);
    }
}
