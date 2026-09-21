package com.dp.deviceops.core.service;

import com.dp.deviceops.core.model.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CliCommandRejectionTest {
    private CommandOutputBlock block(String output) {
        return new CommandOutputBlock(1,"show run",CommandBlockStatus.SUCCEEDED,output,"",output.length(),
                0,false,0,null,Map.of(),List.of(),null,null,false);
    }
    @Test void explicitCliRejectionKeepsOriginalEvidenceAndExitCode() {
        var original=block("show run\r\n% Unknown command. \r\n\r\n<device>");
        var result=CliCommandRejection.classify(original);
        assertEquals(CommandBlockStatus.FAILED,result.status());
        assertEquals("COMMAND_REJECTED",result.outcome());
        assertEquals(original.stdout(),result.stdout());
        assertEquals(0,result.exitCode());
    }
    @Test void configurationTextAndLegacyHistoryAreNotReinterpreted() {
        var configuration=block("show run\nBuilding configuration...\nbanner\n% Unknown command.\n");
        assertSame(configuration,CliCommandRejection.classify(configuration));
        var legacy=CommandOutputBlock.legacy("% Unknown command.","",0,false,Map.of(),null);
        assertSame(legacy,CliCommandRejection.classify(legacy));
    }
}
