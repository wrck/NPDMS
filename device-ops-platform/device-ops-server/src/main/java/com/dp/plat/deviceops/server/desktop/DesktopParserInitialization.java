package com.dp.plat.deviceops.server.desktop;

import com.dp.plat.deviceops.adapter.web.parser.ParserControlProperties;
import com.dp.plat.deviceops.adapter.web.parser.concurrency.ParserWorkerMaintenance;
import com.dp.plat.deviceops.parser.runtime.model.*;
import com.dp.plat.deviceops.parser.runtime.port.ParserReleaseRepository;
import com.dp.plat.deviceops.parser.runtime.service.ParserReleaseService;
import com.dp.plat.deviceops.parser.semantic.ParserCoordinate;
import com.dp.plat.deviceops.parser.semantic.release.ParserReleaseBundleCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.Path;
import java.time.Clock;

/** Fresh installations reuse the shipped parser release and its full validation/publication workflow. */
@Component
@Profile("desktop")
public class DesktopParserInitialization implements ApplicationRunner {
    private final String directory;
    private final ParserReleaseRepository releases;
    private final ParserReleaseService service;
    private final ParserWorkerMaintenance workers;
    private final ParserControlProperties properties;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public DesktopParserInitialization(@Value("${device-ops.desktop.parser-release-directory:}") String directory,
            ParserReleaseRepository releases, ParserReleaseService service, ParserWorkerMaintenance workers,
            ParserControlProperties properties, TransactionTemplate transaction, Clock clock) {
        this.directory=directory; this.releases=releases; this.service=service; this.workers=workers;
        this.properties=properties; this.transaction=transaction; this.clock=clock;
    }

    @Override public void run(ApplicationArguments arguments) throws Exception {
        if (directory.isBlank() || releases.findLogType("device-command-output").isPresent()) return;
        var bundle=new ParserReleaseBundleCodec().decode(Path.of(directory));
        var manifest=bundle.manifest();
        if (!"device-command-output".equals(manifest.logType()) || !"1.4.0".equals(manifest.releaseVersion()))
            throw new IllegalStateException("Unexpected desktop parser release");
        workers.heartbeat();
        // All-or-nothing initialization: a crash cannot leave an unvalidated draft selected for execution.
        transaction.executeWithoutResult(status -> {
            if (releases.findLogType(manifest.logType()).isPresent()) return;
            var now=clock.instant();
            String id="npdms-desktop-device-command-output-1.4.0";
            releases.createLogType(new LogType(manifest.logType(), "设备命令输出", "DAC 安装包内置解析版本", now, now));
            var coordinate=new ParserCoordinate(manifest.logType(), manifest.releaseVersion(), manifest.engineVersion(),
                    manifest.ruleVersion(), manifest.projectionVersion(), manifest.extension()==null?null:manifest.extension().extensionId(),
                    manifest.extension()==null?null:manifest.extension().extensionVersion());
            service.saveDraft(new ParserRelease(id, manifest.logType(), manifest.releaseVersion(), ReleaseState.DRAFT, coordinate, 1, null, now, null), bundle);
            if (!service.validate(id).passed()) throw new IllegalStateException("Desktop parser verification failed");
            service.publish(id);
            service.activate(manifest.logType(), id, null, properties.getActivationMinimumCapableWorkers());
        });
    }
}
