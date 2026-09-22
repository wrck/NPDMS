package cn.iocoder.yudao.module.pms.project.service.projecttemplate;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.domain.template.TemplateDesignerDocument;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** PM-03/PM-11: generated seeds must remain consumable by the real template compiler. */
class ProjectTemplateSeedPackTest {
    private Path asset(String name) {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("sql/template-seeds").resolve(name);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new AssertionError("Seed asset not found: " + name);
    }

    @Test void acceptedConfigurationAndEveryScenarioCompileWithRealCompiler() throws Exception {
        var compiler = new TemplateCompiler();
        var accepted = JsonUtils.parseTree(Files.readString(asset("accepted-fproj009-20260921.json")));
        var original = JsonUtils.parseObject(accepted.path("revision").path("document").toString(), TemplateDesignerDocument.class);
        var acceptedResult = compiler.compileVersioned(original);
        assertTrue(acceptedResult.valid(), () -> acceptedResult.issues().toString());
        var scenarios = JsonUtils.parseTree(Files.readString(asset("prd-project-scenarios.json")));
        assertEquals(6, scenarios.size());
        for (var scenario : scenarios) {
            var document = JsonUtils.parseObject(scenario.path("document").toString(), TemplateDesignerDocument.class);
            var result = compiler.compileVersioned(document);
            assertTrue(result.valid(), () -> scenario.path("code").asText() + ": " + result.issues());
            assertEquals("DRAFT", scenario.path("status").asText());
        }
    }
}
