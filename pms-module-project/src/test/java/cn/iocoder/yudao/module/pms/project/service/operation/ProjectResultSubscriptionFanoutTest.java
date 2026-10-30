package cn.iocoder.yudao.module.pms.project.service.operation;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.pms.project.api.workbinding.result.BusinessResultSource.Validity;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ProjectResultSubscriptionFanoutTest {
    private ResultSubscriptionRecoveryFixture f;
    @BeforeEach void before() throws Exception { f=new ResultSubscriptionRecoveryFixture(); }
    @AfterEach void after() { f.close(); }

    @Test void oneHundredRecipientsPerTransactionAndReplayReusesTheSameDurableMessages() {
        for(long id=502;id<=601;id++)f.subscriptions.insert(f.subscription(id,id));
        var source=f.change(8,f.result("object","result",Validity.CURRENT),true);f.changes.add(source);
        f.fanout.accept(source,0);
        assertEquals(100,count(ResultSubscriptionWakeup.EVENT_TYPE));assertEquals(1,count(ResultSubscriptionFanoutEvent.EVENT_TYPE));
        f.fanout.accept(source,0);assertEquals(100,count(ResultSubscriptionWakeup.EVENT_TYPE));assertEquals(1,count(ResultSubscriptionFanoutEvent.EVENT_TYPE));
        var continuation=JsonUtils.parseObject(f.jdbc.queryForObject("SELECT payload FROM recovery_test_outbox WHERE event_type=?",String.class,ResultSubscriptionFanoutEvent.EVENT_TYPE),ResultSubscriptionFanoutEvent.class);
        assertEquals(600,continuation.afterSubscriptionId());f.fanout.accept(continuation.source(),continuation.afterSubscriptionId());
        assertEquals(101,count(ResultSubscriptionWakeup.EVENT_TYPE));
    }

    @Test void noSubscriptionIsAnAcknowledgedNotificationNotAnInventedBusinessCompletion() {
        f.jdbc.update("UPDATE proj_result_subscription SET phase='RETIRED'");
        var source=f.change(8,f.result("object","result",Validity.CURRENT),true);f.changes.add(source);
        f.fanout.accept(source,0);assertEquals(0,count(ResultSubscriptionWakeup.EVENT_TYPE));
    }

    @Test void rollbackOfOneFanoutPageDoesNotLoseItsRecipients() {
        var source=f.change(8,f.result("object","result",Validity.CURRENT),true);f.changes.add(source);
        f.failOutbox=true;assertThrows(IllegalStateException.class,() -> f.fanout.accept(source,0));
        assertEquals(0,count(ResultSubscriptionWakeup.EVENT_TYPE));f.failOutbox=false;f.fanout.accept(source,0);assertEquals(1,count(ResultSubscriptionWakeup.EVENT_TYPE));
    }

    @Test void fabricatedNotificationCannotWakeSubscribersOfARealChannel() {
        var source=f.change(8,f.result("object","result",Validity.CURRENT),true);
        f.changes.add(f.change(8,f.result("object","other-result",Validity.CURRENT),true));
        assertThrows(IllegalArgumentException.class,() -> f.fanout.accept(source,0));assertEquals(0,count(ResultSubscriptionWakeup.EVENT_TYPE));
    }
    private int count(String type) { return f.jdbc.queryForObject("SELECT COUNT(*) FROM recovery_test_outbox WHERE event_type=?",Integer.class,type); }
}
