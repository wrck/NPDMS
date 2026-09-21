package com.dp.deviceops.adapter.web.callback;

import com.dp.deviceops.core.port.CallbackOutboxPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;

@Component public final class CallbackDispatcher { private static final Logger log=LoggerFactory.getLogger(CallbackDispatcher.class); private final CallbackOutboxPort outbox; private final CallbackProperties p; private final RestClient http; private final Clock clock=Clock.systemUTC(); public CallbackDispatcher(CallbackOutboxPort outbox,CallbackProperties p){this.outbox=outbox;this.p=p;p.validate(); JdkClientHttpRequestFactory factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(p.getConnectTimeout()).build());factory.setReadTimeout(p.getReadTimeout());this.http=RestClient.builder().requestFactory(factory).build();}
 @Scheduled(fixedDelayString="${device-ops.callback.poll-interval:5s}") public void dispatch(){if(!p.isEnabled())return;String worker=UUID.randomUUID().toString();Instant now=clock.instant();for(var e:outbox.claimDue(worker,now,now.plus(p.getLease()),p.getBatchSize()))try{persist(worker,e,networkOutcome(e));}catch(RuntimeException ex){log.warn("Callback persistence failed eventId={} exceptionType={}",e.eventId(),ex.getClass().getSimpleName());}}
 private DeliveryOutcome networkOutcome(CallbackOutboxPort.Event e){URI u=destination(e.destination());if(u==null)return new DeliveryOutcome(false,false,"destination rejected");try{if (e.destination().equals(p.getNpdmsDestination())) { boolean ack = NpdmsCallbackSender.send(e,p,http); return new DeliveryOutcome(ack,!ack,ack?null:"npdms acknowledgement pending"); }int code=http.post().uri(u).headers(h->{h.setBearerAuth(token());h.set("Idempotency-Key",e.eventId());h.set("Content-Type","application/json");}).body(e.payload()).exchange((request,response)->response.getStatusCode().value());return code>=200&&code<300?new DeliveryOutcome(true,false,null):new DeliveryOutcome(false,code==408||code==429||code>=500,"HTTP "+code);}catch(OAuth2Failure oauth){return new DeliveryOutcome(false,true,"oauth2");}catch(Exception x){return new DeliveryOutcome(false,true,"transport");}}
 private URI destination(String value){try{URI u=URI.create(value);return ("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))&&u.getHost()!=null&&p.getAllowedHosts().contains(u.getHost().toLowerCase(Locale.ROOT))?u:null;}catch(Exception ex){return null;}}
 private String token(){try{String body="grant_type=client_credentials"+(p.getScope()==null||p.getScope().isBlank()?"":"&scope="+java.net.URLEncoder.encode(p.getScope(),java.nio.charset.StandardCharsets.UTF_8));Token t=http.post().uri(p.getTokenUri()).header("Content-Type","application/x-www-form-urlencoded").headers(h->h.setBasicAuth(p.getClientId(),p.getClientSecret())).body(body).retrieve().body(Token.class);if(t==null||t.access_token==null||t.access_token.isBlank())throw new OAuth2Failure();return t.access_token;}catch(OAuth2Failure e){throw e;}catch(Exception e){throw new OAuth2Failure();}}
 private void persist(String worker,CallbackOutboxPort.Event e,DeliveryOutcome outcome){if(outcome.delivered())outbox.markDelivered(e.eventId(),worker,clock.instant());else fail(worker,e,outcome.retry(),outcome.safeError());}
 private static final class Token{public String access_token;} private static final class OAuth2Failure extends RuntimeException {} private record DeliveryOutcome(boolean delivered,boolean retry,String safeError) {}
 private void fail(String worker,CallbackOutboxPort.Event e,boolean retry,String error){int n=e.attemptCount()+1;if(!retry||n>=p.getMaxAttempts())outbox.markDeadLetter(e.eventId(),worker,n,error,clock.instant());else {Duration delay=p.getBaseDelay().multipliedBy(1L<<Math.min(20,n-1));if(delay.compareTo(p.getMaxDelay())>0)delay=p.getMaxDelay();outbox.reschedule(e.eventId(),worker,n,clock.instant().plus(delay),error);}}
}
