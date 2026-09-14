<template>
  <section class="rule-slot">
    <header class="slot-heading"
      ><strong>{{ label }}</strong
      ><el-tag v-if="rule" size="small" :type="rule.shared ? 'warning' : 'info'">{{
        rule.shared ? '显式共享' : '独立规则'
      }}</el-tag></header
    >
    <template v-if="rule">
      <el-alert
        v-if="uses.length > 1"
        type="warning"
        :closable="false"
        :title="`修改影响：${uses.join('；')}`"
      />
      <div class="rule-actions" v-if="!readonly">
        <el-button v-if="requiresConfirmation" @click="authorizeSharedEdit"
          >开始修改共享规则</el-button
        >
        <el-button link @click="fork">复制为独立规则</el-button>
        <el-button v-if="!required" link type="danger" @click="emit('update:modelValue', undefined)"
          >移除此处条件</el-button
        >
      </div>
      <RuleDecisionDesigner
        v-if="rule.kind === 'CONDITION' && rule.expression"
        :key="rule.key"
        :model-value="rule.expression"
        :rules="document.rules"
        :disabled="readonly || confirming || requiresConfirmation"
        @update:model-value="updateExpression"
      />
      <el-alert
        v-else
        title="此处需要条件判断，请明确选择决策表的输出比较条件，不能直接使用分类或数值。"
        type="error"
        :closable="false"
      />
      <RuleSimulationPanel :rule-key="rule.key" :rules="document.rules ?? []" />
    </template>
    <el-alert
      v-else
      :title="modelValue ? '本版本缺少引用的规则，不能从其他版本补取。' : emptyText"
      :type="modelValue ? 'error' : 'info'"
      :closable="false"
    />
    <div v-if="!readonly" class="rule-actions">
      <el-button v-if="!rule" @click="create">配置条件</el-button>
      <el-popover placement="bottom-start" :width="320" trigger="click">
        <template #reference><el-button link>使用本版本已有规则</el-button></template>
        <el-checkbox v-model="shareSelection">明确共享，后续修改共同生效</el-checkbox>
        <p class="hint">默认复制成独立规则，不自动建立联动。</p>
        <el-select
          :model-value="undefined"
          placeholder="选择规则"
          filterable
          @update:model-value="selectExisting"
        >
          <el-option
            v-for="item in candidates"
            :key="item.key"
            :value="item.key"
            :label="item.name || item.key"
          />
        </el-select>
      </el-popover>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, provide, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import type { JsonObject, TemplateDesignerDocument } from '@/api/pms/project/project-templates'
import RuleDecisionDesigner from './RuleDecisionDesigner.vue'
import RuleSimulationPanel from './RuleSimulationPanel.vue'
import { constantRule, copyVersionRule, createVersionRule, ruleUses } from './versionRuleModel'
import { businessSources, ruleBusinessSourcesKey } from './ruleBusinessSources'
const props = withDefaults(
  defineProps<{
    document: TemplateDesignerDocument
    modelValue?: string
    label: string
    readonly?: boolean
    required?: boolean
    initialExpression?: JsonObject
    emptyText?: string
  }>(),
  { emptyText: '未配置附加条件。' }
)
const emit = defineEmits<{ 'update:modelValue': [key: string | undefined] }>()
// Vue's reactive provide/inject keeps recursive groups tied to this version, not global editor state.
// https://vuejs.org/guide/components/provide-inject.html#working-with-reactivity
provide(ruleBusinessSourcesKey, computed(() => businessSources(props.document)))
const rule = computed(() => props.document.rules?.find((item) => item.key === props.modelValue))
const candidates = computed(
  () => props.document.rules?.filter((item) => item.kind === 'CONDITION') ?? []
)
const uses = computed(() => (props.modelValue ? ruleUses(props.document, props.modelValue) : []))
const shareSelection = ref(false)
const confirming = ref(false)
const confirmedKey = ref<string>()
const requiresConfirmation = computed(
  () => uses.value.length > 1 && confirmedKey.value !== rule.value?.key
)
watch(
  () => props.modelValue,
  () => {
    confirmedKey.value = undefined
    shareSelection.value = false
  }
)
const create = () =>
  emit(
    'update:modelValue',
    createVersionRule(props.document, props.label, props.initialExpression ?? constantRule(true))
      .key
  )
const fork = () => {
  if (props.modelValue)
    emit('update:modelValue', copyVersionRule(props.document, props.modelValue).key)
}
const authorizeSharedEdit = async () => {
  const current = rule.value
  if (!current) return
  confirming.value = true
  try {
    await ElMessageBox.confirm(`修改影响：${uses.value.join('；')}。`, '确认共享影响', {
      confirmButtonText: '编辑共享规则',
      cancelButtonText: '取消'
    })
    current.shared = true
    confirmedKey.value = current.key
  } catch {
    /* Closing the dialog leaves the shared rule read-only. */
  } finally {
    confirming.value = false
  }
}
const selectExisting = (key: string) => {
  if (shareSelection.value) {
    const selected = props.document.rules?.find((item) => item.key === key)
    if (selected) {
      selected.shared = true
      emit('update:modelValue', key)
    }
  } else emit('update:modelValue', copyVersionRule(props.document, key).key)
}
const updateExpression = async (expression: JsonObject) => {
  const current = rule.value
  if (!current || props.readonly || confirming.value) return
  if (uses.value.length > 1 && confirmedKey.value !== current.key) {
    confirming.value = true
    try {
      await ElMessageBox.confirm(`本次修改影响：${uses.value.join('；')}。`, '修改共享规则', {
        confirmButtonText: '修改共享规则',
        cancelButtonText: '复制为独立规则',
        distinguishCancelAndClose: true
      })
      current.shared = true
      confirmedKey.value = current.key
    } catch (action) {
      if (action === 'cancel') {
        const copied = copyVersionRule(props.document, current.key)
        copied.expression = expression
        emit('update:modelValue', copied.key)
      }
      return
    } finally {
      confirming.value = false
    }
  }
  current.expression = expression
}
</script>

<style scoped>
.rule-slot {
  margin: 16px 0;
}
.slot-heading,
.rule-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 8px 0;
}
.hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
