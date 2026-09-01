package cn.iocoder.yudao.module.pms.cutover.service.plan;

import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanFilePort;
import cn.iocoder.yudao.module.pms.cutover.service.plan.port.CutoverPlanSourcePort;

public final class CutoverPlanControlledPorts {
    private CutoverPlanControlledPorts() {}

    public static final class FilePort implements CutoverPlanFilePort {
        private final FileFact fact;
        public int inspections;
        public FilePort(FileFact fact) { this.fact = fact; }
        @Override public FileFact inspect(Long tenantId, Long actorId, Long projectId, FileHandle handle) { inspections++; return fact; }
        @Override public FileFact lockAndRevalidate(Long tenantId, Long actorId, Long projectId, FileHandle handle) { return fact; }
        @Override public FileFact downloadDraft(Long tenantId, Long actorId, Long projectId, Long planRevisionId) { return fact; }
    }

    public static final class SourcePort implements CutoverPlanSourcePort {
        private final SourceFacts facts;
        public SourcePort(SourceFacts facts) { this.facts = facts; }
        @Override public SourceFacts inspect(Long tenantId, Long actorId, Long taskId) { return facts; }
        @Override public SourceFacts lockAndRevalidate(Long tenantId, Long actorId, SourceFacts expected) { return facts; }
    }
}
