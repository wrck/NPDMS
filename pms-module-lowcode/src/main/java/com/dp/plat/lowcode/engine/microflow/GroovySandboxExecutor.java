package com.dp.plat.lowcode.engine.microflow;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstructorCallExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Groovy 沙箱执行器（借鉴 Mendix/OutSystems 沙箱机制）。
 *
 * <p>使用 {@link SecureASTCustomizer} 配置白名单与黑名单，阻止用户表达式执行危险操作：
 * <ul>
 *   <li>禁用 receivers：System、Runtime、ProcessBuilder、Thread、ClassLoader、File</li>
 *   <li>imports 白名单：java.lang、java.util、java.math</li>
 *   <li>禁止显式 import 与静态 import</li>
 * </ul>
 * 保留基本运算（+ - * / &gt; &lt; == != &amp;&amp; || !）、List/Map 字面量、字符串/数字字面量等。</p>
 *
 * <p>注意：SecureASTCustomizer 并非绝对安全的沙箱，但能拦截绝大多数常见的命令注入与文件/反射访问。</p>
 */
@Component
public class GroovySandboxExecutor {

    private static final Set<String> FORBIDDEN_TYPES = Set.of(
            System.class.getName(),
            Runtime.class.getName(),
            ProcessBuilder.class.getName(),
            Thread.class.getName(),
            Class.class.getName(),
            ClassLoader.class.getName(),
            File.class.getName(),
            groovy.lang.GroovyShell.class.getName(),
            groovy.lang.GroovyClassLoader.class.getName(),
            groovy.lang.GroovySystem.class.getName()
    );

    private static final Set<String> FORBIDDEN_METHODS = Set.of(
            "execute", "getClass", "forName", "getClassLoader", "loadClass",
            "parseClass", "evaluate", "invokeMethod"
    );

    private final CompilerConfiguration compilerConfiguration;

    public GroovySandboxExecutor() {
        SecureASTCustomizer customizer = new SecureASTCustomizer();
        // 不开启间接 import 检查 —— 该选项配合空 importsWhitelist 会拦截所有方法调用（包括 .size()），
        // 过度限制基本运算。改用 receiversBlackList 在 AST 层拦截危险类的方法调用。
        customizer.setIndirectImportCheckEnabled(false);
        // 禁止任何显式 import 语句（空列表 = 无白名单条目）
        customizer.setImportsWhitelist(Collections.emptyList());
        // 允许的 star imports：java.lang / java.util / java.math + groovy.lang（闭包 Closure 为 Groovy 基础语法）
        customizer.setStarImportsWhitelist(Arrays.asList("java.lang", "java.util", "java.math", "groovy.lang"));
        // 禁止静态 import
        customizer.setStaticImportsWhitelist(Collections.emptyList());
        // 禁用危险类的 receivers（方法调用与构造），并阻止通过 GroovyShell/GroovyClassLoader 等绕过沙箱
        customizer.setReceiversBlackList(List.copyOf(FORBIDDEN_TYPES));
        /*
         * receiversBlackList 只约束方法调用的接收者，不能可靠拦截构造表达式。
         * 增加 AST 表达式检查，覆盖 new File(...)、全限定类名以及动态反射入口。
         */
        customizer.addExpressionCheckers(expression -> {
            if (expression instanceof ConstructorCallExpression constructorCall) {
                return !isForbiddenType(constructorCall.getType());
            }
            if (expression instanceof ClassExpression classExpression) {
                return !isForbiddenType(classExpression.getType());
            }
            if (expression instanceof StaticMethodCallExpression staticCall) {
                return !isForbiddenType(staticCall.getOwnerType())
                        && !FORBIDDEN_METHODS.contains(staticCall.getMethod());
            }
            if (expression instanceof MethodCallExpression methodCall) {
                String methodName = methodCall.getMethodAsString();
                return methodName == null || !FORBIDDEN_METHODS.contains(methodName);
            }
            return true;
        });
        this.compilerConfiguration = new CompilerConfiguration();
        this.compilerConfiguration.addCompilationCustomizers(customizer);
    }

    private static boolean isForbiddenType(ClassNode type) {
        return type != null && FORBIDDEN_TYPES.contains(type.getName());
    }

    /**
     * 在沙箱约束下执行 Groovy 表达式。
     *
     * @param binding    变量绑定
     * @param expression Groovy 表达式
     * @return 表达式求值结果
     */
    public Object evaluate(Binding binding, String expression) {
        GroovyShell shell = new GroovyShell(binding, compilerConfiguration);
        return shell.evaluate(expression);
    }
}
