package com.dp.plat.lowcode.engine.microflow;

import groovy.lang.Binding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 循环节点执行器：遍历 iterable，每次把元素放入 variables["_loopItem"]，跳转到 bodyNodeId 执行循环体。
 *
 * <p>采用图回路（loop-back）实现循环：LOOP 节点的默认出边指向循环出口；循环体末尾节点的出边指回 LOOP 节点。
 * 每次进入 LOOP 节点时推进迭代游标，迭代结束后返回 null（由引擎走默认出边到出口）。</p>
 *
 * <p>节点 config: {iterableExpression: "Groovy 表达式（返回 Collection/数组）", bodyNodeId: "循环体起始节点ID"}</p>
 *
 * <p>迭代状态保存在 variables["_loopState"]（含 list 与 index），当前元素保存在 variables["_loopItem"]。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoopExecutor implements MicroflowNodeExecutor {

    /** 存放迭代状态的 variables 键 */
    private static final String LOOP_STATE_KEY = "_loopState";
    /** 当前迭代元素的 variables 键 */
    private static final String LOOP_ITEM_KEY = "_loopItem";

    private final GroovySandboxExecutor groovySandboxExecutor;

    @Override
    public MicroflowNodeType getNodeType() {
        return MicroflowNodeType.LOOP;
    }

    @SuppressWarnings("unchecked")
    @Override
    public String execute(Map<String, Object> nodeDef, MicroflowContext context) {
        Map<String, Object> config = (Map<String, Object>) nodeDef.get("config");
        if (config == null) return null;
        String iterableExpression = (String) config.get("iterableExpression");
        String bodyNodeId = (String) config.get("bodyNodeId");
        if (bodyNodeId == null) return null;

        Map<String, Object> variables = context.getVariables();
        Object existingState = variables.get(LOOP_STATE_KEY);

        if (existingState instanceof Map<?, ?> rawState && rawState.get("list") instanceof List<?> list) {
            // 复用已存在的迭代状态，推进游标
            @SuppressWarnings("unchecked")
            Map<String, Object> state = (Map<String, Object>) rawState;
            int index = ((Number) state.get("index")).intValue() + 1;
            if (index < list.size()) {
                state.put("index", index);
                variables.put(LOOP_ITEM_KEY, list.get(index));
                log.debug("LoopExecutor: 迭代 {}/{} = {}", index + 1, list.size(), list.get(index));
                return bodyNodeId;
            } else {
                // 迭代结束，清理状态并返回 null（引擎走默认出边到出口）
                variables.remove(LOOP_STATE_KEY);
                variables.remove(LOOP_ITEM_KEY);
                log.debug("LoopExecutor: 循环结束，共迭代 {} 次", list.size());
                return null;
            }
        }

        // 首次进入：计算 iterable
        if (iterableExpression == null) return null;
        Binding binding = new Binding(variables);
        Object iterable = groovySandboxExecutor.evaluate(binding, iterableExpression);
        List<Object> list = toList(iterable);
        if (list.isEmpty()) {
            log.debug("LoopExecutor: iterable 为空，跳过循环");
            return null;
        }
        Map<String, Object> loopState = new HashMap<>();
        loopState.put("list", list);
        loopState.put("index", 0);
        variables.put(LOOP_STATE_KEY, loopState);
        variables.put(LOOP_ITEM_KEY, list.get(0));
        log.debug("LoopExecutor: 开始迭代 {}/{} = {}", 1, list.size(), list.get(0));
        return bodyNodeId;
    }

    private List<Object> toList(Object iterable) {
        if (iterable == null) return new ArrayList<>();
        if (iterable instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (iterable.getClass().isArray()) {
            int length = java.lang.reflect.Array.getLength(iterable);
            List<Object> list = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                list.add(java.lang.reflect.Array.get(iterable, i));
            }
            return list;
        }
        throw new IllegalArgumentException("iterableExpression 必须返回 Collection 或数组，实际类型: "
                + iterable.getClass().getName());
    }
}
