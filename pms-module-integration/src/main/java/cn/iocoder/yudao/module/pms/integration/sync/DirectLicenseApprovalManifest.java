package cn.iocoder.yudao.module.pms.integration.sync;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import tools.jackson.core.JsonToken;
import static cn.iocoder.yudao.module.pms.integration.sync.DppmsSecureLicenseSyncTemplate.*;

/** Non-secret frozen facts only. One immutable manifest is retained; actor/Owner authorization is never cached. */
final class DirectLicenseApprovalManifest {
    private static final String GZIP="gzip:";
    private record Cached(String encoded,DirectLicenseApprovalManifest manifest) {}
    private static volatile Cached cached;
    private final List<Approved> facts;
    private final Map<String,Approved> byId;
    private final String ids;
    private DirectLicenseApprovalManifest(List<Approved> facts,Map<String,Approved> byId) {
        this.facts=List.copyOf(facts);this.byId=Collections.unmodifiableMap(byId);
        this.ids=String.join(",",facts.stream().map(Approved::id).toList());
    }
    List<Approved> facts(){return facts;}
    Map<String,Approved> byId(){return byId;}
    String ids(){return ids;}
    static synchronized DirectLicenseApprovalManifest load(SyncDefinition.Source source) {
        try {
            String encoded=Objects.toString(source.parameters().get("approvedLicenseFacts"),"");
            var prior=cached;if(prior!=null && prior.encoded().equals(encoded))return prior.manifest();
            InputStream bytes=encoded.startsWith(GZIP)?new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded.substring(GZIP.length())))):
                    new ByteArrayInputStream(encoded.getBytes(StandardCharsets.UTF_8));
            // Each readTree consumes one member of the approval array; the outer parser checks the final EOF.
            var mapper=tools.jackson.databind.json.JsonMapper.builder().disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
            var all=new ArrayList<Approved>();var index=new HashMap<String,Approved>();
            try(var input=new Bounded(bytes);var parser=mapper.createParser(input)) {
                if(parser.nextToken()!=JsonToken.START_ARRAY)throw new IllegalArgumentException();
                var part=mapper.createArrayNode();
                while(parser.nextToken()!=JsonToken.END_ARRAY) {
                    if(parser.currentToken()!=JsonToken.START_OBJECT)throw new IllegalArgumentException();
                    part.add(mapper.readTree(parser));
                    if(part.size()==200){append(source,part,all,index);part=mapper.createArrayNode();}
                }
                if(!part.isEmpty())append(source,part,all,index);
                if(parser.nextToken()!=null)throw new IllegalArgumentException();
            }
            all.sort(Comparator.comparing(a->new java.math.BigInteger(a.id())));
            var result=new DirectLicenseApprovalManifest(all,index);cached=new Cached(encoded,result);return result;
        }catch(IOException|RuntimeException failure){throw new IllegalArgumentException("LICENSE_APPROVAL_INVALID");}
    }
    private static void append(SyncDefinition.Source source,tools.jackson.databind.node.ArrayNode part,List<Approved> all,Map<String,Approved> index) {
        var subset=source.toBuilder().parameters(Map.of("approvedLicenseFacts",part.toString())).build();
        var parsed=DppmsSecureLicenseSyncTemplate.approved(subset);
        create(new Request(1L,null,parsed));
        for(var fact:parsed)if(index.putIfAbsent(fact.id(),fact)!=null)throw new IllegalArgumentException();
        all.addAll(parsed);
    }
    static String encode(List<Approved> facts) {
        if(facts.isEmpty())return "[]";
        try {
            var output=new ByteArrayOutputStream();
            try(var gzip=new GZIPOutputStream(output)){tools.jackson.databind.json.JsonMapper.builder().build().writeValue(gzip,facts);}
            return GZIP+Base64.getEncoder().encodeToString(output.toByteArray());
        }catch(IOException failure){throw new IllegalArgumentException("LICENSE_APPROVAL_INVALID");}
    }
    private static final class Bounded extends FilterInputStream {
        private long read;
        Bounded(InputStream input){super(input);}
        private void count(int n)throws IOException{if(n>0 && (read+=n)>512L*1024*1024)throw new IOException("LICENSE_APPROVAL_SIZE_LIMIT");}
        @Override public int read()throws IOException{int value=in.read();count(value<0?0:1);return value;}
        @Override public int read(byte[] b,int offset,int length)throws IOException{int n=in.read(b,offset,length);count(n);return n;}
    }
}
