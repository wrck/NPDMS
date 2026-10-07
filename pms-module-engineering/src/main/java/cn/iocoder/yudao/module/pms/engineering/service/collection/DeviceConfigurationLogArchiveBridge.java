package cn.iocoder.yudao.module.pms.engineering.service.collection;

import cn.iocoder.yudao.module.pms.asset.api.device.DeviceConfigurationLogArchiveApi;
import cn.iocoder.yudao.module.pms.platform.api.collection.CollectionBusinessResultReceiver;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.Set;

/** Additive ingress adapter after the legacy receiver has verified the current IMP owner and immutable replay. */
@Aspect @Component @RequiredArgsConstructor
@ConditionalOnProperty(prefix="pms.integration.device-ops", name="enabled", havingValue="true")
public class DeviceConfigurationLogArchiveBridge {
    private final ObjectProvider<DeviceConfigurationLogArchiveApi> archives;
    private final PlatformTransactionManager transactions;
    @Around(value="execution(* cn.iocoder.yudao.module.pms.engineering.service.collection.ImplementationCollectionLogService.receive(..)) && args(result)", argNames="call,result")
    public Object archive(ProceedingJoinPoint call, CollectionBusinessResultReceiver.Result result) throws Throwable {
        if (result == null || !"configuration".equals(result.entry()) || result.externalStatus() == null
                || !Set.of("SUCCESS", "SUCCEEDED").contains(result.externalStatus())) return call.proceed();
        // REQUIRED keeps both writes atomic even when the caller has not opened an outer transaction.
        return new TransactionTemplate(transactions).execute(status -> {
            try {
                Object returned = call.proceed();
                archives.getObject().receiveCollection(new DeviceConfigurationLogArchiveApi.CollectionLog(result.tenantId(), result.actorId(),
                        result.projectId(), result.deviceId(), result.platformTaskId(), result.resultVersion(), result.fileVersionId()));
                return returned;
            } catch (RuntimeException | Error failure) { throw failure; }
            catch (Throwable failure) { throw new IllegalStateException("设备日志接收失败",failure); }
        });
    }
}
