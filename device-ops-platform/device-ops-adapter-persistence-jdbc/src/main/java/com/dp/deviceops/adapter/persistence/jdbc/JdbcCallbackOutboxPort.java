package com.dp.deviceops.adapter.persistence.jdbc;

import com.dp.deviceops.core.port.CallbackOutboxPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant; import java.util.*;

public final class JdbcCallbackOutboxPort implements CallbackOutboxPort {
    private final JdbcClient jdbc; private final TransactionTemplate tx;
    @Override public int redeliverTerminalResult(String collectionId) {
        return jdbc.sql("update device_ops_outbox set dead_lettered_at=null,attempt_count=0,next_attempt_at=current_timestamp,last_error=null "
                + "where aggregate_id=:id and event_type='COLLECTION_TARGET_TERMINAL' and delivered_at is null "
                + "and dead_lettered_at is not null and lease_owner is null")
                .param("id", collectionId).update();
    }
    public JdbcCallbackOutboxPort(JdbcClient jdbc, TransactionTemplate tx){this.jdbc=jdbc;this.tx=tx;}
    public List<Event> claimDue(String worker,Instant now,Instant until,int limit){ return tx.execute(s->{List<String> ids=jdbc.sql("select event_id from device_ops_outbox where delivered_at is null and dead_lettered_at is null and next_attempt_at<=:now and (lease_until is null or lease_until<:now) order by next_attempt_at,event_id limit :limit").param("now",now).param("limit",limit).query(String.class).list(); List<Event> out=new ArrayList<>(); for(String id:ids){if(jdbc.sql("update device_ops_outbox set lease_owner=:w,lease_until=:u where event_id=:id and (lease_until is null or lease_until<:now)").param("w",worker).param("u",until).param("id",id).param("now",now).update()==1){var row=jdbc.sql("select event_id,destination,payload,attempt_count from device_ops_outbox where event_id=:id").param("id",id).query().singleRow();out.add(new Event((String)row.get("EVENT_ID"),(String)row.get("DESTINATION"),(String)row.get("PAYLOAD"),((Number)row.get("ATTEMPT_COUNT")).intValue()));}}return out;}); }
    public void markDelivered(String id,String worker,Instant at){ changed(jdbc.sql("update device_ops_outbox set delivered_at=:at,lease_owner=null,lease_until=null where event_id=:id and lease_owner=:w").param("id",id).param("w",worker).param("at",at).update(),id); }
    public void reschedule(String id,String worker,int attempts,Instant next,String error){changed(jdbc.sql("update device_ops_outbox set attempt_count=:a,next_attempt_at=:n,last_error=:e,lease_owner=null,lease_until=null where event_id=:id and lease_owner=:w").param("id",id).param("w",worker).param("a",attempts).param("n",next).param("e",error).update(),id);}
    public void markDeadLetter(String id,String worker,int attempts,String error,Instant at){changed(jdbc.sql("update device_ops_outbox set attempt_count=:a,dead_lettered_at=:at,last_error=:e,lease_owner=null,lease_until=null where event_id=:id and lease_owner=:w").param("id",id).param("w",worker).param("a",attempts).param("at",at).param("e",error).update(),id);}
    public DeliveryState findDeliveryState(String id){var r=jdbc.sql("select attempt_count,next_attempt_at,delivered_at,dead_lettered_at,last_error from device_ops_outbox where event_id=:id").param("id",id).query().singleRow(); java.sql.Timestamp d=(java.sql.Timestamp)r.get("DELIVERED_AT"),x=(java.sql.Timestamp)r.get("DEAD_LETTERED_AT"),n=(java.sql.Timestamp)r.get("NEXT_ATTEMPT_AT"); return new DeliveryState(d!=null?"DELIVERED":x!=null?"DEAD_LETTER":"PENDING",((Number)r.get("ATTEMPT_COUNT")).intValue(),n.toInstant(),d==null?null:d.toInstant(),x==null?null:x.toInstant(),(String)r.get("LAST_ERROR"));}
    private static void changed(int n,String id){if(n!=1)throw new IllegalStateException("outbox lease lost: "+id);}
}
