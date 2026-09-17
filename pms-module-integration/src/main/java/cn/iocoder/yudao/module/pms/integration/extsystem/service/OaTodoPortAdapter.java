package cn.iocoder.yudao.module.pms.integration.extsystem.service;

import cn.iocoder.yudao.module.pms.integration.extsystem.exception.IntegrationException;
import cn.iocoder.yudao.module.pms.integration.extsystem.model.oa.OaTodoRequest;
import cn.iocoder.yudao.module.pms.workflow.spi.OaTodoPort;
import cn.iocoder.yudao.module.pms.workflow.spi.dto.OaTodoCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the workflow-owned OA port without leaking integration services
 * into workflow. The existing request shape and service APIs remain unchanged.
 *
 * <p>The port's independent transaction contains the integration log writes.
 * Controlled OA failures leave FAILED logs available for retry, then propagate
 * to the listener outside the transaction proxy. Unexpected persistence/runtime
 * failures still roll back; no log durability is promised when the database fails.</p>
 */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = IntegrationException.class)
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
        if (!oaIntegrationService.pushTodo(request)) {
            throw new IntegrationException("oa", "OA todo push returned an unsuccessful result");
        }
    }

    @Override
    public void completeTodo(String businessKey) {
        if (!oaIntegrationService.completeTodo(businessKey)) {
            throw new IntegrationException("oa", "OA todo completion returned an unsuccessful result");
        }
    }
}
