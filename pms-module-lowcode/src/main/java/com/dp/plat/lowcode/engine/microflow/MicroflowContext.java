package com.dp.plat.lowcode.engine.microflow;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 微流执行上下文。
 *
 * <p>持有变量作用域、输入参数、输出结果。Groovy 表达式可直接读写 variables。</p>
 */
@Data
public class MicroflowContext {

    /** 输入参数（只读） */
    private final Map<String, Object> inputs = new HashMap<>();

    /** 变量作用域（可读写） */
    private final Map<String, Object> variables = new HashMap<>();

    /** 输出结果 */
    private Object result;

    /** 是否已终止（遇到 RETURN / END） */
    private boolean terminated = false;

    public MicroflowContext(Map<String, Object> inputs) {
        if (inputs != null) {
            this.inputs.putAll(inputs);
            this.variables.putAll(inputs);
        }
    }

    public Object getVariable(String key) {
        return variables.get(key);
    }

    public void setVariable(String key, Object value) {
        variables.put(key, value);
    }
}
