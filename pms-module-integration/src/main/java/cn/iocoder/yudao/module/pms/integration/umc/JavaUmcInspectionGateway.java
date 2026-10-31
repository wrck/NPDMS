package cn.iocoder.yudao.module.pms.integration.umc;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.pms.integration.api.umc.UmcInspectionApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Java-only UMC protocol. The business Owner retains authorization and durable checkpoints. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "pms.integration.umc", name = "provider", havingValue = "java", matchIfMissing = true)
public class JavaUmcInspectionGateway implements UmcInspectionApi {
    static final int MAX_BYTES = 52_428_800;
    // Wire JSON keeps explicit null task fields and exact integral IDs independently of Spring settings.
    private static final tools.jackson.databind.json.JsonMapper WIRE_JSON = tools.jackson.databind.json.JsonMapper.builder()
            .changeDefaultPropertyInclusion(v -> com.fasterxml.jackson.annotation.JsonInclude.Value.construct(
                    com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS, com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS))
            .build();
    private final UmcInspectionProperties properties;
    private final TransportFactory transports;
    @Autowired public JavaUmcInspectionGateway(UmcInspectionProperties properties) { this(properties, UmcHttpsTransport::new); }
    JavaUmcInspectionGateway(UmcInspectionProperties properties, TransportFactory transports) { this.properties=properties; this.transports=transports; }
    interface Transport extends AutoCloseable { Reply exchange(Call call) throws Exception; @Override void close(); }
    interface TransportFactory { Transport open(UmcInspectionProperties properties) throws Exception; }
    record Call(String route, String method, byte[] body, Map<String,String> headers, long timeoutMillis) { }
    record Reply(int status, byte[] body) { }
    record InputLog(String name, String ip, byte[] content, String sha256) { }

    @Override public synchronized Result inspect(Request request) {
        validate(request);
        validateConfiguration(request.checkpoint());
        var session=new Session(request);
        return session.run();
    }

    private final class Session {
        final Request input;
        final List<InputLog> files;
        final String digest, name;
        final List<String> warnings=new ArrayList<>();
        final long deadline;
        Checkpoint checkpoint;
        Transport transport;
        boolean loggedIn;
        Session(Request request) {
            input=request;checkpoint=request.checkpoint();
            files=request.logs().stream().map(f -> new InputLog(f.fileName(),fileIp(f.fileName()),f.content(),sha(f.content()))).toList();
            if(files.stream().map(InputLog::ip).distinct().count()!=files.size()) throw fail("DUPLICATE_DEVICE_IP");
            var scope=ordered("tenantId",String.valueOf(request.scope().tenantId()),"taskKey",request.scope().taskKey(),"attemptKey",request.scope().attemptKey());
            digest=sha(jsonBytes(ordered("scope",scope,"requestId",request.requestId(),"files",files.stream().map(f -> ordered("name",f.name(),"sha256",f.sha256())).toList())));
            name="NPDMS_"+sha(jsonBytes(List.of(scope,request.requestId()))).substring(0,28);
            checkpoint=request.checkpoint()==null ? new Checkpoint(digest,null,null,"NOT_SENT") : request.checkpoint();
            if(!digest.equals(checkpoint.inputDigest()) || !Set.of("NOT_SENT","UNKNOWN","ACCEPTED").contains(Objects.toString(checkpoint.uploadState(),""))) throw fail("CHECKPOINT_MISMATCH");
            if(request.queryOnly()) update(checkpoint.taskId(),checkpoint.reportId(),"UNKNOWN");
            deadline=System.nanoTime()+properties.getTimeoutSeconds()*1_000_000_000L;
        }
        Failure fail(String code) { return new Failure(code,checkpoint); }
        void update(String task,String report,String state) { checkpoint=new Checkpoint(digest,task,report,state); }
        long remaining() { long ms=(deadline-System.nanoTime())/1_000_000; if(ms<=0) throw fail("UNKNOWN".equals(checkpoint.uploadState()) ? "UPLOAD_OUTCOME_UNKNOWN" : "TIMEOUT");return ms; }
        void pause() throws InterruptedException { Thread.sleep(Math.min(1000,remaining())); }
        Reply send(String path,String method,byte[] body,Map<String,String> headers,boolean cleanup) throws Exception {
            return transport.exchange(new Call(path,method,body,headers,cleanup?3000:Math.min(15000,remaining())));
        }
        Reply get(String path) throws Exception { return send(path,"GET",null,Map.of(),false); }
        Object query(String path) throws Exception {
            Reply response=get(path);if(response.status()!=200)throw fail("QUERY_FAILED");
            Map<String,Object> value=object(parse(response.body()));
            if(value.containsKey("code")) { if(!"200".equals(text(value.get("code"))))throw fail("QUERY_REJECTED");return value.get("data"); }
            return value;
        }
        void post(String path,byte[] body,String type) throws Exception {
            Reply response=send(path,"POST",body,Map.of("content-type",type),false);
            if(response.status()!=200)throw fail("SUBMISSION_REJECTED");
            if(!new String(response.body(),StandardCharsets.UTF_8).isBlank()) {
                Map<String,Object> value=object(parse(response.body()));if(!"200".equals(text(value.get("code"))))throw fail("SUBMISSION_REJECTED");
            }
        }
        List<Map<String,Object>> all(String path) throws Exception {
            var rows=new ArrayList<Map<String,Object>>();
            for(int page=1;page<=500;page++) {
                Map<String,Object> data=object(query(path+"?pagesize=200&current="+page));
                if(!(data.get("list") instanceof List<?> list) || !(data.get("total") instanceof Number total)
                        || total.longValue()<0 || total.doubleValue()!=total.longValue())throw fail("INVALID_PAGE");
                for(Object row:list)rows.add(object(row));
                if(rows.size()>=total.longValue())return rows;
                if(list.isEmpty())throw fail("INCOMPLETE_PAGE_SCAN");
            }
            throw fail("INCOMPLETE_PAGE_SCAN");
        }
        Map<String,Object> findTask() throws Exception {
            var hits=all("/umc-api/dev/inspection/tasklist").stream().filter(t -> name.equals(t.get("name"))).toList();
            if(hits.size()>1)throw fail("AMBIGUOUS_TASK");
            if(hits.isEmpty())return null;
            var task=hits.getFirst();if(!("NPDMS_"+digest).equals(task.get("descr")) || !"1".equals(text(task.get("type"))))throw fail("TASK_INPUT_CONFLICT");
            if(blank(text(task.get("id"))))throw fail("INVALID_TASK");return task;
        }
        Map<String,Object> report() throws Exception {
            var hits=all("/umc-api/dev/inspection/reportlist").stream().filter(r -> Objects.equals(checkpoint.taskId(),text(r.get("taskid")))).toList();
            if(hits.size()>1)throw fail("AMBIGUOUS_REPORT");return hits.isEmpty()?null:hits.getFirst();
        }
        void login() throws Exception {
            get("/UMC/Login!start.action?c=-1486678149");
            var code=object(parse(get("/UMC/code.jsp").body())).get("c");if(code==null)throw fail("LOGIN_PROTOCOL_CHANGED");
            String page=new String(get("/UMC/Login!start.action?c="+encode(text(code))+"&errmsg=").body(),StandardCharsets.UTF_8);
            var challenge=new UmcLoginCipher.Challenge(match(page,"getElementById\\(\"c\"\\)\\.value=\"(-?\\d+)\""),match(page,"\\bcn=\"(-?\\d+)\""),match(page,"\\bcp=\"(-?\\d+)\""));
            var cipher=UmcLoginCipher.login(new UmcLoginCipher.Credentials(properties.getUsername(),properties.getPassword(),challenge));
            String body="c="+encode(challenge.c())+"&q="+encode(cipher.q())+"&w="+encode(cipher.w())+"&p=&e=&smvalidate="+cipher.smvalidate();
            URI base=URI.create(properties.getBaseUrl());String origin=base.getScheme()+"://"+base.getRawAuthority();
            loggedIn=true;
            send("/UMC/Login.action","POST",body.getBytes(StandardCharsets.UTF_8),Map.of("content-type","application/x-www-form-urlencoded","origin",origin,"referer",origin+"/UMC/Login!start.action?c="+encode(text(code))),false);
            try { query("/umc-api/dev/inspection/tasklist?pagesize=1&current=1"); }
            catch(Failure error) { throw fail("LOGIN_FAILED"); }
        }
        byte[] multipart(String boundary) throws Exception {
            var out=new ByteArrayOutputStream();
            for(var file:files) {
                out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"files\"; filename=\""+file.name()+"\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(file.content());out.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"id\"\r\n\r\n"+checkpoint.taskId()+"\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));return out.toByteArray();
        }
        Result run() {
            Result result;
            try {
                transport=transports.open(properties);login();
                var task=findTask();
                if(task==null && input.queryOnly())throw fail("RECOVERY_TASK_NOT_FOUND");
                if(task==null && checkpoint.taskId()!=null)throw fail("CHECKPOINT_TASK_MISSING");
                if(task==null) {
                    try {
                        post("/umc-api/dev/inspection/taskadd",jsonBytes(taskBody()),"application/json;charset=UTF-8");
                        while((task=findTask())==null)pause();
                    }catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw fail("TASK_CREATION_OUTCOME_UNKNOWN");}
                }
                String taskId=text(task.get("id"));if(checkpoint.taskId()!=null && !checkpoint.taskId().equals(taskId))throw fail("CHECKPOINT_TASK_MISMATCH");
                update(taskId,checkpoint.reportId(),checkpoint.uploadState());
                var report=report();
                if(report==null && "NOT_SENT".equals(checkpoint.uploadState())) {
                    String boundary="----npdms"+UUID.randomUUID().toString().replace("-","");byte[] body=multipart(boundary);
                    update(taskId,checkpoint.reportId(),"UNKNOWN");
                    post("/umc-api/dev/inspection/offlinetaskstart",body,"multipart/form-data; boundary="+boundary);
                    update(taskId,checkpoint.reportId(),"ACCEPTED");
                }
                while(report==null || !Boolean.TRUE.equals(report.get("doc"))){pause();report=report();}
                String reportId=text(report.get("id"));if(blank(reportId))throw fail("INVALID_REPORT");
                if(checkpoint.reportId()!=null && !checkpoint.reportId().equals(reportId))throw fail("CHECKPOINT_REPORT_MISMATCH");
                update(taskId,reportId,"ACCEPTED");
                Reply detailReply=get("/umc-api/dev/inspection/report?id="+encode(reportId));if(detailReply.status()!=200)throw fail("DETAIL_FAILED");
                var raw=object(parse(detailReply.body()));
                if(!reportId.equals(text(raw.get("id"))) || !taskId.equals(text(raw.get("taskid"))) || !(raw.get("devlist") instanceof List<?> devices))throw fail("DETAIL_IDENTITY_MISMATCH");
                var ips=new HashSet<>(files.stream().map(InputLog::ip).toList());
                if(devices.size()!=ips.size())throw fail("DETAIL_DEVICE_MISMATCH");
                for(Object row:devices){var d=object(row);if(!ips.remove(text(d.get("deviceip"))) || !(d.get("rulelist") instanceof List<?>))throw fail("DETAIL_DEVICE_MISMATCH");}
                Reply downloaded=send("/umc-api/dev/inspection/report_download","POST",jsonBytes(ordered("id",reportId,"type","doc")),Map.of("content-type","application/json;charset=UTF-8"),false);
                if(downloaded.status()!=200)throw fail("DOC_DOWNLOAD_FAILED");
                try { validateWordXml(downloaded.body()); }catch(Exception error){throw fail("INVALID_DOC");}
                var detail=JsonUtils.parseObject(detailReply.body(),Report.class);
                result=new Result(checkpoint,name+".doc","application/vnd.ms-wordml",sha(downloaded.body()),downloaded.body(),sha(detailReply.body()),detailReply.body(),detail,List.of());
            }catch(Failure failure){throw failure.checkpoint()==null ? fail(failure.code()) : failure;
            }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw fail("INTERRUPTED");
            }catch(Exception failure){throw fail("PROTOCOL_FAILURE");
            }finally{
                if(transport!=null) {
                    if(loggedIn)try{var response=send("/UMC/Logout.action","GET",null,Map.of(),true);if(response.status()!=200 && response.status()!=302)warnings.add("LOGOUT_UNCONFIRMED");}catch(Exception ignored){warnings.add("LOGOUT_UNCONFIRMED");}
                    transport.close();
                }
            }
            return new Result(result.checkpoint(),result.fileName(),result.mediaType(),result.docSha256(),result.doc(),result.detailSha256(),result.rawDetail(),result.detail(),warnings);
        }
        Map<String,Object> taskBody(){return ordered("type",1,"edit",false,"id",null,"name",name,"descr","NPDMS_"+digest,"enabled",true,"custom","","lxr","","lxrPhone","","xjr","","xjrPhone","","whr","","whrPhone","","xmjl","","xmjlPhone","","fwjl","","fwjlPhone","","dev",1,"deviceid",null,"devtype",null,"devmodel",null,"deviceids",List.of(),"exdeviceids",List.of(),"rule",1,"rulelevel",1,"ruletypes",List.of(),"ruleids",List.of(),"time",1,"timedate",null,"timeday",1,"timehour",14400,"interval",1,"doc",true,"pdf",false,"send",false,"recv","");}
    }
    private void validateConfiguration(Checkpoint checkpoint){
        try {
            URI u=URI.create(properties.getBaseUrl());
            if(!"https".equals(u.getScheme()) || blank(u.getHost()) || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null || !(u.getPath().isEmpty() || "/".equals(u.getPath()))
                    || blank(properties.getUsername()) || blank(properties.getPassword()) || properties.getTimeoutSeconds()<1 || properties.getTimeoutSeconds()>1800
                    || (!blank(properties.getCertificateSha256()) && !properties.getCertificateSha256().replace(":","").matches("(?i)[0-9a-f]{64}")))throw new IllegalArgumentException();
        }catch(Exception invalid){throw new Failure("NOT_CONFIGURED",checkpoint);}
    }
    static void validate(Request r){
        if(r==null || r.scope()==null || r.scope().tenantId()==null || !Objects.equals(r.scope().tenantId(),TenantContextHolder.getRequiredTenantId()) || blank(r.scope().taskKey()) || blank(r.scope().attemptKey()) || blank(r.requestId()) || r.requestId().length()>128 || r.logs()==null || r.logs().isEmpty() || r.logs().size()>64)throw new Failure("INVALID_INPUT",r==null?null:r.checkpoint());
        long size=0;for(var log:r.logs()){if(log==null || log.sizeBytes()==0 || (size+=log.sizeBytes())>MAX_BYTES)throw new Failure("INVALID_INPUT",r.checkpoint());fileIp(log.fileName());}
    }
    static String fileIp(String name){
        if(blank(name) || name.length()>255 || Pattern.compile("[\"\\\\/:*?<>|\\p{Cntrl}]").matcher(name).find())throw new Failure("INVALID_FILENAME",null);
        var m=Pattern.compile("^([^()（）]*?)[(（]([^()（）]+)[)）]$").matcher(name);String ip=m.matches()?m.group(2):name;
        String[] parts=ip.split("\\.",-1);if(parts.length!=4)throw new Failure("INVALID_FILENAME",null);
        for(String p:parts)if(!p.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(p)>255)throw new Failure("INVALID_FILENAME",null);return ip;
    }
    static Map<String,Object> ordered(Object... pairs){var result=new LinkedHashMap<String,Object>();for(int i=0;i<pairs.length;i+=2)result.put((String)pairs[i],pairs[i+1]);return result;}
    @SuppressWarnings("unchecked") static Map<String,Object> object(Object value){if(!(value instanceof Map<?,?>))throw new Failure("INVALID_JSON",null);return (Map<String,Object>)value;}
    static Object parse(byte[] bytes){try{return WIRE_JSON.readValue(bytes,Map.class);}catch(Exception error){throw new Failure("INVALID_JSON",null);}}
    static String text(Object value){return value==null?null:value.toString();}
    static byte[] jsonBytes(Object value){return WIRE_JSON.writeValueAsBytes(value);}
    static String sha(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception impossible){throw new IllegalStateException("SHA-256 unavailable");}}
    static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    static String match(String text,String pattern){var m=Pattern.compile(pattern).matcher(text);if(!m.find())throw new Failure("LOGIN_PROTOCOL_CHANGED",null);return m.group(1);}
    static boolean blank(String s){return s==null || s.isBlank();}
    static void validateWordXml(byte[] bytes) throws Exception { UmcWordXml.validate(bytes); }
}
