package com.dp.plat.lowcode.engine.audit;

import com.dp.plat.common.util.SecurityUtils;
import com.dp.plat.lowcode.service.LowCodeConfigAuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置审计日志 AOP 切面（缺口2）。
 *
 * <p>拦截 8 大核心 ConfigService 的写方法（save / saveOrUpdate / update /
 * updateById / removeById / removeByIds / delete / create），自动记录
 * before/after 快照到 {@code pms_lowcode_config_audit_log}。</p>
 *
 * <p><b>覆盖的 ConfigService</b>：
 * <ul>
 *   <li>{@code LowCodeEntityService} → ENTITY</li>
 *   <li>{@code LowCodeFormService} → FORM</li>
 *   <li>{@code LowCodeListService} → LIST</li>
 *   <li>{@code LowCodeTabService} → TAB</li>
 *   <li>{@code LowCodeRelatedPageService} → RELATED_PAGE</li>
 *   <li>{@code LowCodeMicroflowService} → MICROFLOW</li>
 *   <li>{@code LowCodeRuleService} → RULE</li>
 *   <li>{@code LowCodeConnectorService} → CONNECTOR</li>
 * </ul></p>
 *
 * <p><b>异常策略</b>：审计写入为 best-effort，所有异常被吞掉，不阻断主业务。
 * 快照查询失败、序列化失败均仅记 WARN 日志。</p>
 *
 * <p><b>性能说明</b>：写操作本身已含数据库事务，多查一次 before 快照（按 ID
 * 查询）的代价可接受。若配置数据量极大可考虑改用缓存快照。</p>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ConfigAuditAspect {

    private final LowCodeConfigAuditLogService auditLogService;

    /** 配置类型映射：Service 类简单名 → 审计 configType */
    private static final Map<String, String> SERVICE_TO_CONFIG_TYPE = new HashMap<>();
    static {
        SERVICE_TO_CONFIG_TYPE.put("LowCodeEntityServiceImpl", "ENTITY");
        SERVICE_TO_CONFIG_TYPE.put("LowCodeFormServiceImpl", "FORM");
        SERVICE_TO_CONFIG_TYPE.put("LowCodeListServiceImpl", "LIST");
        SERVICE_TO_CONFIG_TYPE.put("LowCodeTabServiceImpl", "TAB");
        SERVICE_TO_CONFIG_TYPE.put("LowCodeRelatedPageServiceImpl", "RELATED_PAGE");
        SERVICE_TO_CONFIG_TYPE.put("LowCodeMicroflowServiceImpl", "MICROFLOW");
        SERVICE_TO_CONFIG_TYPE.put("LowCodeRuleServiceImpl", "RULE");
        SERVICE_TO_CONFIG_TYPE.put("LowCodeConnectorServiceImpl", "CONNECTOR");
    }

    /** 拦截 8 大 ConfigService 实现类的写方法 */
    private static final String POINTCUT =
            "(execution(* com.dp.plat.lowcode.service.impl.LowCodeEntityServiceImpl.save*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeEntityServiceImpl.update*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeEntityServiceImpl.remove*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeEntityServiceImpl.delete*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeFormServiceImpl.save*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeFormServiceImpl.update*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeFormServiceImpl.remove*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeFormServiceImpl.delete*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeListServiceImpl.save*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeListServiceImpl.update*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeListServiceImpl.remove*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeListServiceImpl.delete*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeTabServiceImpl.save*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeTabServiceImpl.update*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeTabServiceImpl.remove*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeTabServiceImpl.delete*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeRelatedPageServiceImpl.save*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeRelatedPageServiceImpl.update*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeRelatedPageServiceImpl.remove*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeRelatedPageServiceImpl.delete*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeMicroflowServiceImpl.save*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeMicroflowServiceImpl.update*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeMicroflowServiceImpl.remove*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeMicroflowServiceImpl.delete*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeRuleServiceImpl.save*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeRuleServiceImpl.update*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeRuleServiceImpl.remove*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeRuleServiceImpl.delete*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeConnectorServiceImpl.save*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeConnectorServiceImpl.update*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeConnectorServiceImpl.remove*(..)) ||"
            + " execution(* com.dp.plat.lowcode.service.impl.LowCodeConnectorServiceImpl.delete*(..)))";

    /** 配置类型映射：Service 类简单名 → Spring bean name（用于反射查询快照） */
    private static final Map<String, String> SERVICE_TO_BEAN_NAME = new HashMap<>();
    static {
        SERVICE_TO_BEAN_NAME.put("LowCodeEntityServiceImpl", "lowCodeEntityServiceImpl");
        SERVICE_TO_BEAN_NAME.put("LowCodeFormServiceImpl", "lowCodeFormServiceImpl");
        SERVICE_TO_BEAN_NAME.put("LowCodeListServiceImpl", "lowCodeListServiceImpl");
        SERVICE_TO_BEAN_NAME.put("LowCodeTabServiceImpl", "lowCodeTabServiceImpl");
        SERVICE_TO_BEAN_NAME.put("LowCodeRelatedPageServiceImpl", "lowCodeRelatedPageServiceImpl");
        SERVICE_TO_BEAN_NAME.put("LowCodeMicroflowServiceImpl", "lowCodeMicroflowServiceImpl");
        SERVICE_TO_BEAN_NAME.put("LowCodeRuleServiceImpl", "lowCodeRuleServiceImpl");
        SERVICE_TO_BEAN_NAME.put("LowCodeConnectorServiceImpl", "lowCodeConnectorServiceImpl");
    }

    @Around(POINTCUT)
    public Object auditConfigWrite(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String methodName = method.getName();
        String serviceImplName = signature.getDeclaringType().getSimpleName();
        String configType = SERVICE_TO_CONFIG_TYPE.get(serviceImplName);

        if (configType == null) {
            // 非关注的 Service，直接放行
            return joinPoint.proceed();
        }

        String action = resolveAction(methodName);
        if (action == null) {
            // 非写方法（如 saveDesign 命中 save* 但其实也应记，按 CREATE/UPDATE 处理）
            // 这里采用宽松策略：save*/create* → CREATE，update* → UPDATE，delete*/remove* → DELETE
            action = "UPDATE";
        }

        Object[] args = joinPoint.getArgs();
        Object beforeSnapshot = null;
        Long configId = extractId(args);

        // 对于 UPDATE/DELETE，调用前查询当前快照
        if (("UPDATE".equals(action) || "DELETE".equals(action)) && configId != null) {
            beforeSnapshot = safeLoadById(serviceImplName, configId);
        }

        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            // 主操作失败也记录一条审计（before 已有，after 为异常信息）
            try {
                String actor = SecurityUtils.getCurrentUsername();
                String configCode = extractCode(beforeSnapshot, args);
                auditLogService.record(actor, configType, configId, configCode, action,
                        beforeSnapshot, null, "FAILED: " + e.getMessage());
            } catch (Exception ignore) {
                // 审计失败完全忽略
            }
            throw e;
        }

        // 主操作成功，记录 after 快照
        try {
            String actor = SecurityUtils.getCurrentUsername();
            Object afterSnapshot = result;
            // 对于 DELETE，after 自然为 null；对于 save/update，result 即为更新后的实体
            if (afterSnapshot == null && configId != null && !"DELETE".equals(action)) {
                afterSnapshot = safeLoadById(serviceImplName, configId);
            }
            // 对于 CREATE，从 result 中取 id（save 后实体 id 已回填）
            if ("CREATE".equals(action) && configId == null) {
                configId = extractIdFromResult(result);
            }
            String configCode = extractCode(beforeSnapshot != null ? beforeSnapshot : result, args);
            String diffSummary = buildDiffSummary(action, configType, configId, configCode);
            auditLogService.record(actor, configType, configId, configCode, action,
                    beforeSnapshot, afterSnapshot, diffSummary);
        } catch (Exception e) {
            log.warn("[ConfigAudit] 切面记录审计失败: service={}, method={}, err={}",
                    serviceImplName, methodName, e.getMessage());
        }
        return result;
    }

    /** 根据方法名解析动作类型 */
    private String resolveAction(String methodName) {
        if (methodName == null) return null;
        if (methodName.startsWith("save") || methodName.startsWith("create")
                || methodName.startsWith("add") || methodName.startsWith("insert")) {
            return "CREATE";
        }
        if (methodName.startsWith("update") || methodName.startsWith("modify")) {
            return "UPDATE";
        }
        if (methodName.startsWith("delete") || methodName.startsWith("remove")) {
            return "DELETE";
        }
        if (methodName.startsWith("publish")) {
            return "PUBLISH";
        }
        if (methodName.startsWith("rollback")) {
            return "ROLLBACK";
        }
        if (methodName.startsWith("promote")) {
            return "PROMOTE";
        }
        return null;
    }

    /** 从方法参数中提取配置 ID（适用于 updateById(id, ...) / removeById(id) / delete(id)） */
    private Long extractId(Object[] args) {
        if (args == null || args.length == 0) return null;
        // 第一个参数为 Long/Integer → 视为 ID
        Object first = args[0];
        if (first instanceof Long l) return l;
        if (first instanceof Integer i) return i.longValue();
        if (first instanceof Number n) return n.longValue();
        // 第一个参数为实体对象 → 通过反射取 id
        Long idFromEntity = reflectGetId(first);
        if (idFromEntity != null) return idFromEntity;
        return null;
    }

    /** 从方法返回结果中提取配置 ID（适用于 save(entity) 后实体 id 已回填） */
    private Long extractIdFromResult(Object result) {
        return reflectGetId(result);
    }

    /** 反射调用 getId()（best-effort） */
    private Long reflectGetId(Object obj) {
        if (obj == null) return null;
        try {
            Method getId = obj.getClass().getMethod("getId");
            Object id = getId.invoke(obj);
            if (id instanceof Long l) return l;
            if (id instanceof Number n) return n.longValue();
            if (id instanceof String s) {
                try { return Long.parseLong(s); } catch (NumberFormatException e) { return null; }
            }
        } catch (Exception e) {
            // 无 getId 方法或调用失败，忽略
        }
        return null;
    }

    /** 反射调用 getCode() 提取配置编码（best-effort） */
    private String extractCode(Object snapshot, Object[] args) {
        if (snapshot != null) {
            String code = reflectGetString(snapshot, "getCode");
            if (code != null) return code;
        }
        if (args != null && args.length > 0 && args[0] != null) {
            String code = reflectGetString(args[0], "getCode");
            if (code != null) return code;
        }
        return null;
    }

    private String reflectGetString(Object obj, String getter) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(getter);
            Object v = m.invoke(obj);
            return v == null ? null : v.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** 通过对应 Service 反射调用 getById 查询快照（best-effort） */
    private Object safeLoadById(String serviceImplName, Long id) {
        if (id == null) return null;
        try {
            String beanName = SERVICE_TO_BEAN_NAME.get(serviceImplName);
            if (beanName == null) return null;
            // 切面内不直接持有 8 大 Service 引用（避免循环依赖），通过 ServiceImpl 的 getById
            // 由 MyBatis-Plus ServiceImpl 提供。这里采用 lazy lookup：在切面方法内通过
            // SpringApplicationContextHolder 获取 Bean 后调用 getById。
            Object bean = com.dp.plat.lowcode.util.SpringApplicationContextHolder
                    .getBean(beanName);
            if (bean == null) return null;
            Method getById = bean.getClass().getMethod("getById", Object.class);
            return getById.invoke(bean, id);
        } catch (Exception e) {
            log.debug("[ConfigAudit] 查询 before 快照失败: service={}, id={}, err={}",
                    serviceImplName, id, e.getMessage());
            return null;
        }
    }

    /** 构造变更摘要 */
    private String buildDiffSummary(String action, String configType, Long configId, String configCode) {
        StringBuilder sb = new StringBuilder();
        sb.append(action).append(" ").append(configType);
        if (configCode != null) {
            sb.append(" [").append(configCode).append("]");
        } else if (configId != null) {
            sb.append(" #").append(configId);
        }
        return sb.toString();
    }
}
