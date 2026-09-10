package cn.iocoder.yudao.module.pms.project.service.taskbusiness;

import java.util.List;

public record TaskBusinessLinkedFacts(String factVersion, List<TaskBusinessLinkFact> links) {
    public TaskBusinessLinkedFacts { links = List.copyOf(links); }
}
