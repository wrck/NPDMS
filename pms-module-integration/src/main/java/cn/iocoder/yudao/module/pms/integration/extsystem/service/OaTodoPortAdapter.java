package cn.iocoder.yudao.module.pms.integration.extsystem.service;

import cn.iocoder.yudao.module.pms.integration.extsystem.model.oa.OaTodoRequest;
import cn.iocoder.yudao.module.pms.workflow.spi.OaTodoPort;
import cn.iocoder.yudao.module.pms.workflow.spi.dto.OaTodoCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * OA 待办端口适配 Bean（实现 pms-module-workflow 声明的 {@link OaTodoPort} 扩展点，
 * 委托到本模块 {@link OaIntegrationService}）。
 *
 * <p>源工程 pms-workflow 直接依赖 pms-integration 的 Service，目标体系下改为
 * 依赖倒置：workflow 声明 OaTodoPort，本模块实现并注册为 Spring Bean。
 * 独立 Bean 委托到既有 {@code OaIntegrationService}（旧接口与原有功能保持不变），
 * 字段转换保持 OaTodoCommand → OaTodoRequest 一一映射。</p>
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