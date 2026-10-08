package cn.iocoder.yudao.module.pms.platform.support.business;

import java.lang.reflect.Modifier;
import java.util.*;

/** Internal detached entity copies must retain fields hidden from the HTTP JSON representation. */
final class BusinessEntityCopies {
    private BusinessEntityCopies() { }
    @SuppressWarnings("unchecked")
    static <E> E copy(E source) { return (E) copy(source,new IdentityHashMap<>()); }
    private static Object copy(Object source,IdentityHashMap<Object,Object> copies) {
        if(source==null || source instanceof String || source instanceof Number || source instanceof Boolean
                || source instanceof Character || source instanceof Enum<?> || source instanceof java.time.temporal.TemporalAccessor) return source;
        if(copies.containsKey(source))return copies.get(source);
        if(source instanceof List<?> values) {
            var target=new ArrayList<>();copies.put(source,target);values.forEach(value->target.add(copy(value,copies)));return target;
        }
        if(source instanceof Map<?,?> values) {
            var target=new LinkedHashMap<>();copies.put(source,target);values.forEach((key,value)->target.put(copy(key,copies),copy(value,copies)));return target;
        }
        if(source instanceof Set<?> values) {
            var target=new LinkedHashSet<>();copies.put(source,target);values.forEach(value->target.add(copy(value,copies)));return target;
        }
        try {
            var constructor=source.getClass().getDeclaredConstructor();
            if(!constructor.trySetAccessible())throw new IllegalStateException("Business copy constructor is inaccessible");
            var target=constructor.newInstance();copies.put(source,target);
            for(Class<?> type=source.getClass();type!=Object.class;type=type.getSuperclass())for(var field:type.getDeclaredFields()) {
                if(Modifier.isStatic(field.getModifiers()) || field.isSynthetic())continue;
                if(!field.trySetAccessible())throw new IllegalStateException("Business copy field is inaccessible: "+field.getName());
                field.set(target,copy(field.get(source),copies));
            }
            return target;
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Business entity cannot be detached: "+source.getClass().getName(),failure);}
    }
}
