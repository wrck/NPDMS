package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FrozenOperationContractTest {
    private String declaration() {
        return "{\"version\":1,\"operations\":[{\"operationCode\":\"OP\",\"operationVersion\":1,"
                + "\"pre\":{\"mode\":\"NONE\"},\"post\":{\"mode\":\"NONE\"}}],\"programs\":{}}";
    }
    @Test void absentBindingRetainsLegacyMeaning() { assertNull(FrozenOperationContract.read(null)); }
    @Test void explicitNullIsNotLegacy() {
        assertThrows(IllegalArgumentException.class, () -> FrozenOperationContract.read(JsonUtils.parseTree("null")));
    }
    @Test void emptyRuleClosureIsValidOnlyForNoneChecks() {
        assertNotNull(FrozenOperationContract.read(JsonUtils.parseTree(declaration())));
        assertThrows(IllegalArgumentException.class, () -> FrozenOperationContract.read(JsonUtils.parseTree(
                declaration().replace("\"pre\":{\"mode\":\"NONE\"}", "\"pre\":{\"mode\":\"RULE\",\"ruleKey\":\"missing\"}"))));
    }
    @Test void extraProgramsCannotBeSmuggledIntoTheFrozenClosure() {
        assertThrows(IllegalArgumentException.class, () -> FrozenOperationContract.read(JsonUtils.parseTree(
                declaration().replace("\"programs\":{}", "\"programs\":{\"extra\":{}}"))));
    }
    @Test void authoringWithoutCompiledClosureIsNotAnExecutableContract() {
        assertThrows(IllegalArgumentException.class, () -> FrozenOperationContract.read(JsonUtils.parseTree(
                declaration().replace(",\"programs\":{}", ""))));
    }
    @Test void unknownContractVersionFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> FrozenOperationContract.read(JsonUtils.parseTree(
                declaration().replace("\"version\":1", "\"version\":2"))));
    }
}
