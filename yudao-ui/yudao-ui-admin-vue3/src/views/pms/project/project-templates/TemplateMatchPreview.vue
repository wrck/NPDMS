<template>
  <section aria-label="模板适用规则预演">
    <p>按创建字段试算。未提供的字段为未知；明确空值与未提供不同。未配置适用条件的模板不限。</p>
    <el-alert v-if="failure" :title="failure" type="error" :closable="false" />
    <el-button v-if="!fields.length" :loading="loading" @click="loadFields">读取字段目录</el-button>
    <el-form label-position="top" @submit.prevent="run">
      <el-form-item label="试算字段">
        <el-select v-model="selected" multiple filterable placeholder="选择需要提供的创建字段" aria-label="试算字段">
          <el-option v-for="field in fields" :key="field.code" :label="field.label" :value="field.code" />
        </el-select>
      </el-form-item>
      <el-form-item v-for="field in selectedFields" :key="field.code" :label="field.label">
        <div class="fact-input">
          <el-select v-model="states[field.code]" :aria-label="`${field.label}输入状态`">
            <el-option label="未提供（未知）" value="MISSING" />
            <el-option label="明确空值" value="NULL" />
            <el-option label="提供值" value="VALUE" />
          </el-select>
          <template v-if="states[field.code] === 'VALUE'">
            <el-select v-if="field.valueType === 'BOOLEAN'" v-model="values[field.code]" :aria-label="field.label">
              <el-option label="是" :value="true" /><el-option label="否" :value="false" />
            </el-select>
            <el-input-number v-else-if="field.valueType === 'NUMBER'" :model-value="numberValue(field.code)" :aria-label="field.label" @update:model-value="values[field.code] = $event" />
            <el-input v-else :model-value="textValue(field.code)" @update:model-value="values[field.code] = $event" :aria-label="field.label" placeholder="请输入字段值（编码字段填写编码）" />
          </template>
        </div>
      </el-form-item>
      <el-button native-type="submit" type="primary" :loading="matching" :disabled="loading || !fields.length">执行预演</el-button>
    </el-form>
    <template v-if="result">
      <el-result v-if="result.outcome === 'MATCHED' && result.matched" icon="success" :title="`唯一命中：${result.matched.code} - ${result.matched.name}`" />
      <el-result v-else-if="result.outcome === 'NO_MATCH'" icon="warning" title="无匹配模板" />
      <el-alert v-else title="同优先级多匹配，需明确选择模板" type="warning" :closable="false" />
      <ul><li v-for="(conflict, index) in result.conflicts" :key="index">{{ conflict }}</li></ul>
      <TemplateMatchDiagnostics :evaluations="result.evaluations ?? []" />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { matchPreview, type JsonValue, type MatchRespVO } from '@/api/pms/project/project-templates'
import { getRuleFields, type RuleField } from '@/api/pms/project/project-templates/rules'
import TemplateMatchDiagnostics from './TemplateMatchDiagnostics.vue'
import { errorText } from './editorModel'

const fields = ref<RuleField[]>([])
const selected = ref<string[]>([])
const states = reactive<Record<string, 'MISSING' | 'NULL' | 'VALUE'>>({})
const values = reactive<Record<string, string | number | boolean | undefined>>({})
const numberValue = (code: string) => typeof values[code] === 'number' ? values[code] as number : undefined
const textValue = (code: string) => typeof values[code] === 'string' ? values[code] as string : ''
const selectedFields = computed(() => fields.value.filter((field) => selected.value.includes(field.code)))
const loading = ref(false)
const matching = ref(false)
const failure = ref('')
const result = ref<MatchRespVO>()
let generation = 0
let disposed = false
const facts = computed(() => Object.fromEntries(selectedFields.value.flatMap((field) => {
  if (states[field.code] === 'NULL') return [[field.code, null]]
  if (states[field.code] === 'VALUE' && values[field.code] !== undefined) return [[field.code, values[field.code] as JsonValue]]
  return []
})) as Record<string, JsonValue>)
watch(facts, () => { ++generation; result.value = undefined; matching.value = false }, { flush: 'sync' })
const loadFields = async () => {
  loading.value = true; failure.value = ''
  try {
    const catalog = await getRuleFields()
    if (disposed) return
    fields.value = catalog.filter((field) => field.availableAtCreation)
    for (const field of fields.value) states[field.code] = 'MISSING'
  } catch (error) { if (!disposed) failure.value = errorText(error) }
  finally { if (!disposed) loading.value = false }
}
const run = async () => {
  const current = ++generation
  matching.value = true; failure.value = ''; result.value = undefined
  try {
    const response = await matchPreview({ facts: facts.value })
    if (current === generation) result.value = response
  } catch (error) { if (current === generation) failure.value = errorText(error) }
  finally { if (current === generation) matching.value = false }
}
onMounted(loadFields)
onBeforeUnmount(() => { disposed = true; ++generation })
</script>

<style scoped>
.fact-input { display: flex; flex-wrap: wrap; gap: 8px; width: 100%; }
.fact-input > * { flex: 1; min-width: 160px; }
p { color: var(--el-text-color-secondary); }
</style>
