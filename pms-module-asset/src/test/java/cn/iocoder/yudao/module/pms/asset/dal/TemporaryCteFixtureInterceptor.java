package cn.iocoder.yudao.module.pms.asset.dal;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import java.sql.Connection;
import java.util.*;
import java.util.regex.Pattern;

/** MySQL cannot reopen connection-private fixture tables through nested CTEs. Materialize each
 * actual CTE body separately and clone repeated fixture references. This proves row semantics,
 * not the execution plan or performance of the complete production statement. */
@Intercepts(@Signature(type=StatementHandler.class,method="prepare",args={Connection.class,Integer.class}))
final class TemporaryCteFixtureInterceptor implements Interceptor {
    private final Configuration configuration;
    private int sequence;
    TemporaryCteFixtureInterceptor(Configuration configuration) { this.configuration=configuration; }
    @Override public Object intercept(Invocation invocation) throws Throwable {
        var bound=((StatementHandler)invocation.getTarget()).getBoundSql();
        String text=bound.getSql();
        if (!text.stripLeading().startsWith("WITH")) return invocation.proceed();
        var connection=(Connection)invocation.getArgs()[0];
        var matches=Pattern.compile("(?:WITH\\s+|\\),\\s*)(project_list_\\w+)\\s+AS\\s*\\(").matcher(text).results().toList();
        if(matches.isEmpty()) return invocation.proceed();
        var tables=new LinkedHashMap<String,String>();
        var fixtureTables=new HashSet<>(List.of("ast_device","proj_project","imp_eng_installation",
                "com_project_contract_relation","com_shipment_contract_reference","com_shipment_package",
                "ast_device_shipment","ast_device_relationship","ast_product_official_info"));
        int offset=0,lastEnd=0;
        for(int index=0;index<matches.size();index++) {
            var match=matches.get(index);
            int end=index+1<matches.size()?matches.get(index+1).start():closingParenthesis(text,match.end());
            String body=text.substring(match.end(),end);
            for(var entry:tables.entrySet()) body=body.replaceAll("\\b"+entry.getKey()+"\\b",entry.getValue());
            body=cloneReferences(connection,body,fixtureTables);
            String table="tmp_cte_fixture_"+(++sequence);
            int count=(int)body.chars().filter(ch -> ch=='?').count();
            try(var statement=connection.prepareStatement("CREATE TEMPORARY TABLE "+table+" AS "+body)) {
                for(int parameter=0;parameter<count;parameter++) {
                    String property=bound.getParameterMappings().get(offset+parameter).getProperty();
                    Object value=bound.hasAdditionalParameter(property)?bound.getAdditionalParameter(property)
                            :configuration.newMetaObject(bound.getParameterObject()).getValue(property);
                    statement.setObject(parameter+1,value);
                }
                statement.execute();
            }
            offset+=count;tables.put(match.group(1),table);fixtureTables.add(table);lastEnd=end+1;
        }
        String remaining=text.substring(lastEnd);
        for(var entry:tables.entrySet()) remaining=remaining.replaceAll("\\b"+entry.getKey()+"\\b",entry.getValue());
        remaining=cloneReferences(connection,remaining,fixtureTables);
        var meta=SystemMetaObject.forObject(bound);
        meta.setValue("sql",remaining);
        meta.setValue("parameterMappings",new ArrayList<>(bound.getParameterMappings().subList(offset,bound.getParameterMappings().size())));
        return invocation.proceed();
    }
    private int closingParenthesis(String text,int start) {
        int depth=1;
        for(int index=start;index<text.length();index++) {
            if(text.charAt(index)=='(')depth++;
            if(text.charAt(index)==')' && --depth==0) return index;
        }
        throw new IllegalStateException("Unclosed fixture CTE");
    }
    private String cloneReferences(Connection connection,String sql,Set<String> tables) throws Exception {
        var matcher=Pattern.compile("(?i)\\b(FROM|JOIN)\\s+(\\w+)\\b").matcher(sql);
        var result=new StringBuilder();var seen=new HashSet<String>();
        while(matcher.find()) {
            String name=matcher.group(2),replacement=matcher.group();
            if(tables.contains(name) && !seen.add(name)) {
                String copy="tmp_cte_copy_"+(++sequence);
                try(var statement=connection.createStatement()) {
                    statement.execute("CREATE TEMPORARY TABLE "+copy+" AS SELECT * FROM "+name);
                }
                replacement=matcher.group(1)+" "+copy;
            }
            matcher.appendReplacement(result,replacement);
        }
        matcher.appendTail(result);return result.toString();
    }
}
