<script setup lang="ts">
/**
 * 微流节点参数面板（右侧，借鉴 Mendix Microflows 属性面板）。
 *
 * <p>根据选中节点的 type 渲染对应的参数配置表单（config 字段名与后端 MicroflowNodeExecutor 对齐）：</p>
 * <ul>
 *   <li>ASSIGN：target（目标变量）+ expression（Groovy 表达式）</li>
 *   <li>CONDITION：expression（Groovy 布尔表达式）+ trueBranch/falseBranch 目标节点</li>
 *   <li>LOOP：iterableExpression（Groovy 表达式）+ bodyNodeId（循环体起始节点）</li>
 *   <li>CALL_SERVICE：beanName + methodName + target（结果变量，可选）+ args（Groovy 表达式）</li>
 *   <li>CALL_MICROFLOW：microflowCode（下拉）+ inputsExpression（Groovy 表达式）</li>
 *   <li>CALL_RULE：ruleCode（下拉）+ inputsExpression（Groovy 表达式）</li>
 *   <li>CALL_CONNECTOR：connectorCode（下拉）+ inputsExpression（Groovy 表达式）</li>
 *   <li>THROW_EXCEPTION：errorMessage + errorCode</li>
 *   <li>RETURN：expression（Groovy 返回值表达式）</li>
 *   <li>START/END：无参数</li>
 * </ul>
 *
 * <p>表达式字段统一使用 ExpressionEditor（language=groovy，对齐后端 GroovySandboxExecutor），
 * 可绑定变量/字段补全。</p>
 */
import { computed, reactive, watch } from 'vue'
import ExpressionEditor from '@/components/ExpressionEditor/index.vue'
import type { BindingItem } from '@/components/ExpressionEditor/bindings'
import type {
  MicroflowNode,
  MicroflowNodeType,
  MicroflowVariable
} from '@/api/lowcode-microflow'
import type { LowCodeMicroflow } from '@/api/lowcode-microflow'
import type { LowCodeRule } from '@/api/lowcode-rule'
import type { LowCodeConnector } from '@/api/lowcode-connector'

defineOptions({ name: 'MicroflowNodeParamPanel' })

const props = defineProps<{
  node: MicroflowNode | null
  /** 画布上所有节点（用于 CONDITION/LOOP 选择目标节点） */
  nodes: MicroflowNode[]
  /** 微流下拉数据（CALL_MICROFLOW 用） */
  microflowOptions: LowCodeMicroflow[]
  /** 规则下拉数据（CALL_RULE 用） */
  ruleOptions: LowCodeRule[]
  /** 连接器下拉数据（CALL_CONNECTOR 用） */
  connectorOptions: LowCodeConnector[]
  /** 可用变量（输入 + 局部，传给 ExpressionEditor 用于补全） */
  variables: MicroflowVariable[]
}>()

const emit = defineEmits<{
  (e: 'update:node', node: MicroflowNode): void
}>()

/** 当前编辑的节点 config 副本（深度响应） */
const config = reactive<Record<string, unknown>>({})

/**
 * 仅在节点 ID 变化（切换选中节点）时重置 config，
 * 避免编辑过程中父组件回写 config 导致的来回重置（丢焦点/丢光标）。
 * 撤销/重做场景由父组件通过 :key 重建本组件来强制刷新。
 */
watch(
  () => props.node?.id,
  () => {
    for (const k of Object.keys(config)) delete config[k]
    if (props.node) Object.assign(config, JSON.parse(JSON.stringify(props.node.config || {})))
  },
  { immediate: true }
)

/** 同步 config 到父组件 */
function syncConfig() {
  if (!props.node) return
  emit('update:node', {
    ...props.node,
    config: JSON.parse(JSON.stringify(config))
  })
}

/** 节点标题双向绑定 */
const label = computed<string>({
  get: () => props.node?.label || '',
  set: (v) => {
    if (!props.node) return
    emit('update:node', { ...props.node, label: v })
  }
})

/** ExpressionEditor 用的 binding items（输入 + 局部变量） */
const bindingItems = computed<BindingItem[]>(() =>
  (props.variables || []).map((v) => ({ name: v.name, type: v.type }))
)

/** 用于分支/循环目标节点选择（排除当前节点） */
const targetOptions = computed(() =>
  (props.nodes || [])
    .filter((n) => n.id !== props.node?.id)
    .map((n) => ({ label: `${n.label} (${n.type})`, value: n.id }))
)

const TYPE_LABELS: Record<MicroflowNodeType, string> = {
  START: '开始节点',
  END: '结束节点',
  ASSIGN: '赋值节点',
  CONDITION: '条件分支节点',
  LOOP: '循环节点',
  CALL_SERVICE: '调用服务节点',
  CALL_MICROFLOW: '调用子微流节点',
  CALL_RULE: '调用规则节点',
  CALL_CONNECTOR: '调用连接器节点',
  THROW_EXCEPTION: '抛出异常节点',
  RETURN: '返回节点'
}

function getTypeLabel(t: MicroflowNodeType): string {
  return TYPE_LABELS[t] || t
}
</script>

<template>
  <div class="node-param-panel">
    <div v-if="!node" class="empty-state">
      <el-icon><InfoFilled /></el-icon>
      <div>请选中节点查看/编辑参数</div>
    </div>

    <template v-else>
      <div class="panel-header">
        <el-tag size="small" type="info">{{ node.type }}</el-tag>
        <span class="header-title">{{ getTypeLabel(node.type) }}</span>
      </div>

      <el-form label-width="90px" size="small" class="param-form">
        <el-form-item label="节点标题">
          <el-input v-model="label" placeholder="节点显示名称" @change="syncConfig" />
        </el-form-item>

        <!-- START / END 无额外参数 -->
        <template v-if="node.type === 'START' || node.type === 'END'">
          <div class="form-tip">该节点类型无额外参数配置。</div>
        </template>

        <!-- ASSIGN -->
        <template v-else-if="node.type === 'ASSIGN'">
          <el-form-item label="目标变量">
            <el-input v-model="(config.target as string)" placeholder="如 result" @change="syncConfig" />
          </el-form-item>
          <el-form-item label="表达式">
            <ExpressionEditor
              v-model="(config.expression as string)"
              language="groovy"
              :variables="bindingItems"
              :height="160"
              @update:model-value="syncConfig"
            />
          </el-form-item>
        </template>

        <!-- CONDITION -->
        <template v-else-if="node.type === 'CONDITION'">
          <el-form-item label="条件表达式">
            <ExpressionEditor
              v-model="(config.expression as string)"
              language="groovy"
              :variables="bindingItems"
              :height="160"
              @update:model-value="syncConfig"
            />
          </el-form-item>
          <el-form-item label="true 分支">
            <el-select v-model="(config.trueBranch as string)" placeholder="选择 true 跳转节点" clearable @change="syncConfig">
              <el-option v-for="o in targetOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="false 分支">
            <el-select v-model="(config.falseBranch as string)" placeholder="选择 false 跳转节点" clearable @change="syncConfig">
              <el-option v-for="o in targetOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </template>

        <!-- LOOP -->
        <template v-else-if="node.type === 'LOOP'">
          <el-form-item label="迭代表达式">
            <ExpressionEditor
              v-model="(config.iterableExpression as string)"
              language="groovy"
              :variables="bindingItems"
              :height="120"
              @update:model-value="syncConfig"
            />
          </el-form-item>
          <el-form-item label="循环体起点">
            <el-select v-model="(config.bodyNodeId as string)" placeholder="选择循环体起始节点" clearable @change="syncConfig">
              <el-option v-for="o in targetOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item>
        </template>

        <!-- CALL_SERVICE -->
        <template v-else-if="node.type === 'CALL_SERVICE'">
          <el-form-item label="Bean 名称">
            <el-input v-model="(config.beanName as string)" placeholder="如 userService" @change="syncConfig" />
          </el-form-item>
          <el-form-item label="方法名">
            <el-input v-model="(config.methodName as string)" placeholder="如 getById" @change="syncConfig" />
          </el-form-item>
          <el-form-item label="结果变量">
            <el-input v-model="(config.target as string)" placeholder="可选，结果写入该变量" @change="syncConfig" />
          </el-form-item>
          <el-form-item label="参数表达式">
            <ExpressionEditor
              v-model="(config.args as string)"
              language="groovy"
              :variables="bindingItems"
              :height="120"
              @update:model-value="syncConfig"
            />
          </el-form-item>
        </template>

        <!-- CALL_MICROFLOW -->
        <template v-else-if="node.type === 'CALL_MICROFLOW'">
          <el-form-item label="目标微流">
            <el-select v-model="(config.microflowCode as string)" placeholder="选择子微流" filterable @change="syncConfig">
              <el-option
                v-for="m in microflowOptions"
                :key="m.code"
                :label="`${m.name} (${m.code})`"
                :value="m.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="输入表达式">
            <ExpressionEditor
              v-model="(config.inputsExpression as string)"
              language="groovy"
              :variables="bindingItems"
              :height="120"
              @update:model-value="syncConfig"
            />
          </el-form-item>
        </template>

        <!-- CALL_RULE -->
        <template v-else-if="node.type === 'CALL_RULE'">
          <el-form-item label="目标规则">
            <el-select v-model="(config.ruleCode as string)" placeholder="选择规则" filterable @change="syncConfig">
              <el-option
                v-for="r in ruleOptions"
                :key="r.code"
                :label="`${r.name} (${r.code})`"
                :value="r.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="输入表达式">
            <ExpressionEditor
              v-model="(config.inputsExpression as string)"
              language="groovy"
              :variables="bindingItems"
              :height="120"
              @update:model-value="syncConfig"
            />
          </el-form-item>
        </template>

        <!-- CALL_CONNECTOR -->
        <template v-else-if="node.type === 'CALL_CONNECTOR'">
          <el-form-item label="目标连接器">
            <el-select v-model="(config.connectorCode as string)" placeholder="选择连接器" filterable @change="syncConfig">
              <el-option
                v-for="c in connectorOptions"
                :key="c.code"
                :label="`${c.name} (${c.code})`"
                :value="c.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="输入表达式">
            <ExpressionEditor
              v-model="(config.inputsExpression as string)"
              language="groovy"
              :variables="bindingItems"
              :height="120"
              @update:model-value="syncConfig"
            />
          </el-form-item>
        </template>

        <!-- THROW_EXCEPTION -->
        <template v-else-if="node.type === 'THROW_EXCEPTION'">
          <el-form-item label="错误消息">
            <el-input v-model="(config.errorMessage as string)" placeholder="如 订单不存在" @change="syncConfig" />
          </el-form-item>
          <el-form-item label="错误码">
            <el-input v-model="(config.errorCode as string)" placeholder="如 ORDER_NOT_FOUND" @change="syncConfig" />
          </el-form-item>
        </template>

        <!-- RETURN -->
        <template v-else-if="node.type === 'RETURN'">
          <el-form-item label="返回值表达式">
            <ExpressionEditor
              v-model="(config.expression as string)"
              language="groovy"
              :variables="bindingItems"
              :height="120"
              @update:model-value="syncConfig"
            />
          </el-form-item>
        </template>
      </el-form>
    </template>
  </div>
</template>

<style scoped lang="scss">
.node-param-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.panel-header {
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  gap: 8px;
  background: #fafafa;

  .header-title {
    font-size: 13px;
    font-weight: 600;
    color: #303133;
  }
}

.param-form {
  padding: 10px 12px;
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 13px;
  gap: 6px;

  .el-icon {
    font-size: 32px;
  }
}

.form-tip {
  font-size: 12px;
  color: #909399;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  margin: 8px 0;
}
</style>
