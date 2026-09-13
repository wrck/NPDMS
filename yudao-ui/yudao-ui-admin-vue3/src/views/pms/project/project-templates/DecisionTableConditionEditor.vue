<template>
  <div class="decision-condition">
    <span>决策表：{{ table?.name || String(parameters.ruleKey || '未配置') }}</span>
    <el-button :disabled="disabled" @click="visible = true">配置决策表及输出判断</el-button>
    <el-dialog
      v-model="visible"
      title="决策表条件"
      width="90%"
      append-to-body
      :before-close="close"
    >
      <el-alert
        v-if="parameters.ruleKey"
        type="info"
        :closable="false"
        title="引用本版本策略：这里不修改共享表内容，只配置本条件的输出判断。"
      />
      <DecisionTableEditor
        v-if="table"
        ref="editor"
        :model-value="table"
        :readonly="disabled || !!parameters.ruleKey"
        @update:model-value="updateTable"
        @outputs="outputs = $event"
      />
      <el-form label-position="top" :disabled="disabled" class="output-condition">
        <el-form-item label="用于判断的输出字段">
          <el-select
            :model-value="parameters.fieldCode"
            placeholder="选择输出"
            @update:model-value="chooseOutput"
          >
            <el-option
              v-for="output in outputs"
              :key="output.name"
              :value="output.name"
              :label="`${output.name}（${output.type}）`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="比较方式"
          ><el-select
            :model-value="parameters.operator"
            @update:model-value="set('operator', $event)"
            ><el-option value="=" label="等于" /><el-option value="!=" label="不等于" /><el-option
              v-if="parameters.valueType === 'NUMBER'"
              value=">="
              label="大于等于" /><el-option
              v-if="parameters.valueType === 'NUMBER'"
              value="<="
              label="小于等于" /></el-select
        ></el-form-item>
        <el-form-item label="比较值">
          <el-select
            v-if="parameters.valueType === 'BOOLEAN'"
            :model-value="parameters.value"
            @update:model-value="set('value', $event)"
            ><el-option :value="true" label="是" /><el-option :value="false" label="否"
          /></el-select>
          <el-input
            v-else
            :model-value="String(parameters.value ?? '')"
            @update:model-value="set('value', $event)"
          />
        </el-form-item>
        <el-form-item label="多行输出"
          ><el-select
            :model-value="parameters.quantifier ?? 'ANY'"
            @update:model-value="set('quantifier', $event)"
            ><el-option value="ANY" label="任一输出满足" /><el-option
              value="ALL"
              label="全部输出满足（至少一项）" /></el-select
        ></el-form-item>
      </el-form>
      <template #footer
        ><el-button v-if="parameters.ruleKey && !disabled" @click="forkTable"
          >复制表到此规则独立编辑</el-button
        ><el-button type="primary" @click="close()">完成配置</el-button></template
      >
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import type { JsonObject, JsonValue } from '@/api/pms/project/project-templates'
import type {
  DecisionTableDefinition,
  VersionRule
} from '@/api/pms/project/project-templates/rules'
import DecisionTableEditor from './DecisionTableEditor.vue'
const props = defineProps<{ parameters: JsonObject; rules?: VersionRule[]; disabled?: boolean }>()
const emit = defineEmits<{ change: [parameters: JsonObject] }>()
const visible = ref(false)
const outputs = ref<{ name: string; type: string }[]>([])
const editor = ref<InstanceType<typeof DecisionTableEditor>>()
const table = computed<DecisionTableDefinition | undefined>(() =>
  props.parameters.ruleKey
    ? props.rules?.find((rule) => rule.key === props.parameters.ruleKey)?.decision
    : (props.parameters.table as unknown as DecisionTableDefinition | undefined)
)
const set = (key: string, value: JsonValue) => emit('change', { ...props.parameters, [key]: value })
const updateTable = (value: DecisionTableDefinition) => {
  if (!props.parameters.ruleKey) set('table', value as unknown as JsonObject)
}
const chooseOutput = (name: string) => {
  const type = outputs.value.find((output) => output.name === name)?.type
  emit('change', {
    ...props.parameters,
    fieldCode: name,
    valueType: type === 'boolean' ? 'BOOLEAN' : type === 'number' ? 'NUMBER' : 'TEXT',
    value: type === 'boolean' ? true : ''
  })
}
const forkTable = () => {
  if (!table.value) return
  emit('change', {
    ...props.parameters,
    ruleKey: undefined,
    table: { ...JSON.parse(JSON.stringify(table.value)), key: `table_${crypto.randomUUID()}` }
  })
}
const close = async (done?: () => void) => {
  await editor.value?.flush()
  visible.value = false
  done?.()
}
defineExpose({ flush: async () => editor.value?.flush() })
</script>

<style scoped>
.decision-condition {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.output-condition {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
</style>
