package cn.iocoder.yudao.module.pms.platform.api.businessmodel.execution;

import cn.iocoder.yudao.module.pms.platform.api.businessmodel.Completeness;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.access.BusinessFieldFilter;
import cn.iocoder.yudao.module.pms.platform.api.businessmodel.fact.FactObservation;
import java.math.BigDecimal;
import java.util.*;

/** Shared FIELD_CONDITION semantics, also used by composite/collection conditions and both execution backends. */
public final class BusinessConditionEvaluator {
    private BusinessConditionEvaluator() { }
    public static RuleVerdict evaluate(List<BusinessFieldFilter> conditions,Map<String,FactObservation> facts,String version){
        return evaluate(new BusinessCondition(BusinessCondition.Kind.ALL,null,null,List.of(),conditions.stream()
                .map(c->new BusinessCondition(BusinessCondition.Kind.FIELD,c.fieldCode(),c.operator(),c.values(),List.of())).toList()),facts,version);
    }
    public static RuleVerdict evaluate(BusinessCondition condition,Map<String,FactObservation> facts,String version){
        var missing=new LinkedHashSet<String>();var failures=new LinkedHashSet<String>();
        Boolean matched=evaluate(condition,facts,missing,failures,0);
        // A known false closes ALL safely. Unknown/incomplete evidence can never produce a pass.
        if(Boolean.TRUE.equals(matched) && !missing.isEmpty())matched=null;
        if(Boolean.FALSE.equals(matched))missing.addAll(failures);
        return new RuleVerdict(matched,matched!=null,version,List.copyOf(missing));
    }
    private static Boolean evaluate(BusinessCondition c,Map<String,FactObservation> facts,Set<String> missing,Set<String> failures,int depth){
        if(c==null || c.kind()==null || depth>32)throw new IllegalArgumentException("BUSINESS_CONDITION_INVALID");
        if(c.kind()==BusinessCondition.Kind.ALL || c.kind()==BusinessCondition.Kind.ANY || c.kind()==BusinessCondition.Kind.NOT){
            if(c.kind()==BusinessCondition.Kind.NOT && c.children().size()!=1)throw new IllegalArgumentException("BUSINESS_CONDITION_INVALID");
            var values=new ArrayList<Boolean>();var childFailures=new LinkedHashSet<String>();
            for(var child:c.children())values.add(evaluate(child,facts,missing,childFailures,depth+1));
            if(c.kind()==BusinessCondition.Kind.NOT){
                if(Boolean.TRUE.equals(values.getFirst()))failures.add("否定条件未满足");
                return values.getFirst()==null?null:!values.getFirst();
            }
            var result=combine(values,c.kind()==BusinessCondition.Kind.ALL);
            if(Boolean.FALSE.equals(result))failures.addAll(childFailures);
            return result;
        }
        String code=c.kind()==BusinessCondition.Kind.DELIVERY?"$delivery:"+c.fieldCode():c.fieldCode();
        var fact=facts.get(code);
        if(fact==null || fact.availability()==FactObservation.Availability.UNKNOWN || fact.availability()==FactObservation.Availability.UNAVAILABLE
                || fact.completeness()!=Completeness.COMPLETE){missing.add("事实缺失或不可读: "+code);return null;}
        if(c.kind()==BusinessCondition.Kind.FIELD || c.kind()==BusinessCondition.Kind.DELIVERY){
            var result=matches(fact,c.operator(),c.values());if(result==null)missing.add("条件不可比较: "+code);
            if(Boolean.FALSE.equals(result))failures.add("条件未满足: "+code+" "+c.operator());
            return result;
        }
        var raw=fact.availability()==FactObservation.Availability.EMPTY?List.of():fact.value();
        if(!(raw instanceof List<?> rows)){missing.add("关系集合不可读: "+code);return null;}
        var results=new ArrayList<Boolean>();var memberFailures=new LinkedHashSet<String>();
        for(var row:rows){
            if(!(row instanceof Map<?,?> map)){missing.add("关系成员不可读: "+code);return null;}
            var member=new LinkedHashMap<String,FactObservation>();
            map.forEach((key,value)->{
                if(key instanceof String name)member.put(name,value instanceof FactObservation observation?observation:
                        new FactObservation(name,value==null?FactObservation.Availability.EMPTY:FactObservation.Availability.VALUE,value,fact.basis(),fact.completeness()));
            });
            results.add(evaluate(new BusinessCondition(BusinessCondition.Kind.ALL,null,null,List.of(),c.children()),member,missing,memberFailures,depth+1));
        }
        if(c.kind()==BusinessCondition.Kind.MEMBER_ANY || c.kind()==BusinessCondition.Kind.MEMBER_ALL){
            var result=combine(results,c.kind()==BusinessCondition.Kind.MEMBER_ALL);
            if(Boolean.FALSE.equals(result)){failures.add("关系集合条件未满足: "+code);failures.addAll(memberFailures);}
            return result;
        }
        if(results.contains(null))return null;
        var result=matches(new FactObservation(code,FactObservation.Availability.VALUE,results.stream().filter(Boolean.TRUE::equals).count(),fact.basis(),Completeness.COMPLETE),c.operator(),c.values());
        if(result==null)missing.add("集合数量不可比较: "+code);
        if(Boolean.FALSE.equals(result))failures.add("集合数量条件未满足: "+code+" "+c.operator());
        return result;
    }
    private static Boolean combine(List<Boolean> values,boolean all){
        if(values.contains(!all))return !all;
        return values.contains(null)?null:all;
    }
    private static Boolean matches(FactObservation fact,BusinessFieldFilter.Operator operator,List<Object> expected){
        if(operator==null)return null;
        boolean empty=fact.availability()==FactObservation.Availability.EMPTY;
        final List<Object> values;
        try{values=expected.stream().map(value->operand(fact.value(),value)).toList();}
        catch(java.time.format.DateTimeParseException invalid){return null;}
        return switch(operator){
            case IS_NULL -> empty;
            case NOT_NULL -> !empty;
            case EQ -> {if(empty)yield values.isEmpty();if(values.isEmpty())yield null;yield values.stream().anyMatch(v->equal(fact.value(),v));}
            case NE -> {if(empty)yield !values.isEmpty();if(values.isEmpty())yield null;yield values.stream().noneMatch(v->equal(fact.value(),v));}
            case IN -> {if(empty)yield false;if(values.isEmpty())yield null;yield values.stream().anyMatch(v->equal(fact.value(),v));}
            case LIKE -> {if(empty)yield false;if(!(fact.value() instanceof String text)||values.isEmpty())yield null;yield values.stream().anyMatch(v->text.contains(String.valueOf(v)));}
            case GT,GTE,LT,LTE -> {if(empty)yield false;yield ordered(fact.value(),values,operator);}
        };
    }
    private static Object operand(Object actual,Object expected){
        if(expected instanceof String text){
            if(actual instanceof java.time.LocalDate)return java.time.LocalDate.parse(text);
            if(actual instanceof java.time.LocalDateTime)return java.time.LocalDateTime.parse(text.replace(' ','T'));
        }
        return expected;
    }
    private static boolean equal(Object a,Object b){return a instanceof Number && b instanceof Number?new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))==0:Objects.equals(a,b);}
    @SuppressWarnings({"rawtypes","unchecked"})
    private static Boolean ordered(Object value,List<Object> expected,BusinessFieldFilter.Operator op){
        if(expected.size()!=1 || value==null || expected.getFirst()==null)return null;
        var other=expected.getFirst();int cmp;
        if(value instanceof Number && other instanceof Number)cmp=new BigDecimal(value.toString()).compareTo(new BigDecimal(other.toString()));
        else if(value.getClass().equals(other.getClass()) && value instanceof Comparable comparable)cmp=comparable.compareTo(other);
        else return null;
        return switch(op){case GT->cmp>0;case GTE->cmp>=0;case LT->cmp<0;case LTE->cmp<=0;default->null;};
    }
}
