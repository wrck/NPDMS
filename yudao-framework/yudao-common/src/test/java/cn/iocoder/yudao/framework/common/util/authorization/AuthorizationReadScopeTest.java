package cn.iocoder.yudao.framework.common.util.authorization;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationReadScopeTest {
    @Test void reusesEmptyResultsOnlyInsideScopeAndCleansUpOnFailure() {
        var calls=new AtomicInteger();
        var loader=(java.util.function.Supplier<Integer>)calls::incrementAndGet;
        assertEquals(1,AuthorizationReadScope.read("key",loader));
        try(var scope=AuthorizationReadScope.open()) {
            assertEquals(2,AuthorizationReadScope.read("key",loader));
            assertEquals(2,AuthorizationReadScope.read("key",loader));
            assertNull(AuthorizationReadScope.read("empty",()->null));
            assertNull(AuthorizationReadScope.read("empty",()->fail("Null result should be reused")));
            assertThrows(IllegalStateException.class,()->AuthorizationReadScope.read("failure",()->{throw new IllegalStateException();}));
            assertEquals(3,AuthorizationReadScope.read("failure",loader));
        }
        assertEquals(4,AuthorizationReadScope.read("key",loader));
        assertThrows(IllegalStateException.class,()->{
            try(var scope=AuthorizationReadScope.open()) {
                AuthorizationReadScope.read("key",loader);
                throw new IllegalStateException();
            }
        });
        assertEquals(6,AuthorizationReadScope.read("key",loader));
    }

    @Test void doesNotShareBetweenThreadsAndRestoresNestedScope() throws Exception {
        var calls=new AtomicInteger();
        try(var outer=AuthorizationReadScope.open()) {
            assertEquals(1,AuthorizationReadScope.read("key",calls::incrementAndGet));
            try(var inner=AuthorizationReadScope.open()) {
                assertEquals(2,AuthorizationReadScope.read("key",calls::incrementAndGet));
            }
            assertEquals(1,AuthorizationReadScope.read("key",calls::incrementAndGet));
            var result=new AtomicInteger();
            var thread=new Thread(()->result.set(AuthorizationReadScope.read("key",calls::incrementAndGet)));
            thread.start();thread.join();assertEquals(3,result.get());
            assertEquals(1,AuthorizationReadScope.read("key",calls::incrementAndGet));
        }
    }
}
