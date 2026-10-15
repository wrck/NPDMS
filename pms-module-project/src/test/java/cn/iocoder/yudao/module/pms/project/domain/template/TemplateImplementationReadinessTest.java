package cn.iocoder.yudao.module.pms.project.domain.template;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class TemplateImplementationReadinessTest {
    @Test void nativeFileTaskCannotImpersonateHardwareOwnerFact() {
        var task = new TemplateExecutionSnapshot.TaskContract(); task.setCode("FILES");
        var binding = new TemplateExecutionSnapshot.BindingContract(); binding.setType("TASK_NATIVE"); task.setBinding(binding);
        var policy = new TemplateImplementationReadiness(Map.of("EXE_02", "FILES"));
        assertThrows(IllegalArgumentException.class, () -> policy.requiredSources(List.of(task)));
    }
    @Test void unknownSourceOrUnknownTaskCodeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TemplateImplementationReadiness(Map.of("EXE_05", "T")));
        var policy = new TemplateImplementationReadiness(Map.of("EXE_02", "MISSING"));
        assertThrows(IllegalArgumentException.class, () -> policy.requiredSources(List.of()));
    }
}
