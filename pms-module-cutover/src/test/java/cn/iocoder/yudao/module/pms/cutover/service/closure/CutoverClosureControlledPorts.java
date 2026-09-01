package cn.iocoder.yudao.module.pms.cutover.service.closure;

import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureCollectionPort;
import cn.iocoder.yudao.module.pms.cutover.service.closure.port.CutoverClosureFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.taskv2.port.CutoverProjectScopePort;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** 仅供CUT单元/集成测试显式组装的确定性Owner事实。 */
final class CutoverClosureControlledPorts {

    private CutoverClosureControlledPorts() {
    }

    static final class ProjectScopes implements CutoverProjectScopePort {
        private final long projectId;
        private final long scopeVersion;

        ProjectScopes(long projectId, long scopeVersion) {
            this.projectId = projectId;
            this.scopeVersion = scopeVersion;
        }

        @Override
        public ProjectScopeFact inspect(Long actorId, Long projectId, String action) {
            return new ProjectScopeFact(projectId, scopeVersion, true);
        }

        @Override
        public ProjectScopeFact lockAndRevalidate(Long actorId, Long projectId, String action,
                                                  long expectedProjectScopeVersion) {
            return new ProjectScopeFact(projectId, scopeVersion, true);
        }

        @Override
        public Set<Long> resolveAllCurrent(Long actorId, String action) {
            return Set.of(projectId);
        }
    }

    static final class Files implements CutoverClosureFilePort {
        @Override
        public FileFact inspect(FileExpectation expectation) {
            return fact(expectation);
        }

        @Override
        public FileFact lockAndRevalidate(FileExpectation expectation) {
            return fact(expectation);
        }

        private static FileFact fact(FileExpectation expectation) {
            return new FileFact(expectation.artifactId(), expectation.versionNo(), expectation.referenceKey(),
                    expectation.fileFactVersion(), expectation.scopeVersion(), expectation.sha256());
        }
    }

    static final class Collections implements CutoverClosureCollectionPort {
        private final AtomicLong sequence = new AtomicLong(1000);
        private final Map<CollectionIntentIdentity, DispatchFact> facts = new LinkedHashMap<>();
        private final Clock clock;
        private DispatchOutcome nextOutcome = DispatchOutcome.ACCEPTED;
        private String nextFailureCode;
        private CollectionIntentIdentity lastIdentity;

        Collections(Clock clock) {
            this.clock = clock;
        }

        void nextDispatch(DispatchOutcome outcome, String failureCode) {
            nextOutcome = outcome;
            nextFailureCode = failureCode;
        }

        @Override
        public DispatchFact request(CollectionRequest request) {
            lastIdentity = request.identity();
            return facts.computeIfAbsent(request.identity(), ignored -> new DispatchFact(
                    "controlled-collection-" + sequence.incrementAndGet(), nextOutcome,
                    nextOutcome == DispatchOutcome.FAILED ? nextFailureCode : null,
                    LocalDateTime.now(clock)));
        }

        @Override
        public DispatchLookup inspectByIntent(CollectionIntentIdentity identity) {
            DispatchFact fact = facts.get(identity);
            return fact == null ? new DispatchLookup(LookupStatus.NOT_FOUND, null)
                    : new DispatchLookup(LookupStatus.FOUND, fact);
        }

        CollectionIntentIdentity lastIdentity() {
            return lastIdentity;
        }
    }
}
