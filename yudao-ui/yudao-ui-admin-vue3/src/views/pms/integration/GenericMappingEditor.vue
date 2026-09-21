<template>
  <div class="generic-mapping">
    <el-form label-width="120px" @submit.prevent>
      <el-form-item label="配置模板">
        <el-select v-model="templateKey" aria-label="配置模板" @change="loadTemplate">
          <el-option
            v-for="item in templates"
            :key="item.key"
            :value="item.key"
            :label="item.name"
          />
          <el-option value="custom" label="自定义字段映射" />
        </el-select>
      </el-form-item>
      <el-form-item label="任务标识">
        <el-input
          v-model="model.taskKey"
          aria-label="任务标识"
          placeholder="保存后保持稳定，例如 shipment-package"
        />
      </el-form-item>
    </el-form>
    <el-alert
      type="info"
      :closable="false"
      title="每个来源按步骤顺序写入目标。字段映射、唯一键和关联查找均可配置；预览不会写入业务表。"
    />
    <el-collapse v-model="openSources">
      <el-collapse-item
        v-for="(source, sourceIndex) in model.sources"
        :key="sourceIndex"
        :name="sourceIndex"
        :title="source.object || '新来源'"
      >
        <el-form label-width="120px" @submit.prevent>
          <el-form-item label="来源对象标识"
            ><el-input v-model="source.object" aria-label="来源对象标识"
          /></el-form-item>
          <el-form-item label="来源证据名称"
            ><el-input v-model="source.sourceObject" aria-label="来源证据名称"
          /></el-form-item>
          <el-form-item label="读取方式"
            ><el-radio-group v-model="source.readMode"
              ><el-radio value="TABLE">来源表</el-radio
              ><el-radio value="SQL">只读查询</el-radio></el-radio-group
            ></el-form-item
          >
          <el-form-item v-if="source.readMode === 'TABLE'" label="来源表">
            <el-input v-model="source.table" aria-label="来源表" />
            <el-button :loading="readingSource === source.object" @click="loadColumns(source)"
              >读取来源字段</el-button
            >
          </el-form-item>
          <el-form-item v-else label="只读 SQL"
            ><el-input v-model="source.sql" type="textarea" :rows="5" aria-label="只读 SQL"
          /></el-form-item>
          <el-form-item label="来源主键列"
            ><el-input v-model="source.sourceKey" aria-label="来源主键列"
          /></el-form-item>
          <el-form-item label="增量时间列"
            ><el-input
              v-model="source.updatedAt"
              aria-label="增量时间列"
              placeholder="增量任务必填"
          /></el-form-item>
          <el-form-item label="主目标步骤"
            ><el-select v-model="source.primaryTarget" clearable placeholder="默认最后一个步骤"
              ><el-option
                v-for="step in source.targets"
                :key="step.name"
                :value="step.name"
                :label="step.name" /></el-select
          ></el-form-item>
        </el-form>
        <section v-for="(step, stepIndex) in source.targets" :key="stepIndex" class="target-step">
          <div class="step-heading"
            ><strong>写入步骤 {{ stepIndex + 1 }}</strong
            ><el-button text type="danger" @click="source.targets?.splice(stepIndex, 1)"
              >删除步骤</el-button
            ></div
          >
          <el-form label-width="120px" @submit.prevent>
            <el-form-item label="步骤名称"
              ><el-input v-model="step.name" aria-label="步骤名称"
            /></el-form-item>
            <el-form-item label="目标表"
              ><el-select v-model="step.table" aria-label="目标表" @change="changeTarget(step)"
                ><el-option
                  v-for="target in writableTargets"
                  :key="target.table"
                  :value="target.table"
                  :label="`${target.label}（${target.table}）`" /></el-select
            ></el-form-item>
            <el-form-item label="业务唯一键"
              ><el-select
                :model-value="step.keys.join(',')"
                aria-label="业务唯一键"
                @update:model-value="step.keys = $event.split(',')"
                ><el-option
                  v-for="keys in targetFor(step)?.keys"
                  :key="keys.join(',')"
                  :value="keys.join(',')"
                  :label="keys.join(' + ')" /></el-select
            ></el-form-item>
            <el-form-item label="写入方式"
              ><el-select v-model="step.mode" aria-label="步骤写入方式"
                ><el-option label="新增并更新" value="UPSERT" /><el-option
                  label="仅新增，重复报错"
                  value="INSERT_ONLY" /><el-option
                  label="新增，跳过已存在"
                  value="INSERT_IGNORE" /><el-option
                  label="仅更新已有记录"
                  value="UPDATE_ONLY" /></el-select
            ></el-form-item>
            <el-form-item label="空值策略"
              ><el-radio-group v-model="step.nullPolicy"
                ><el-radio value="IGNORE">保留目标已有值</el-radio
                ><el-radio value="OVERWRITE">使用空值覆盖</el-radio></el-radio-group
              ></el-form-item
            >
            <el-form-item label="允许更新字段"
              ><el-select v-model="step.updateColumns" multiple aria-label="允许更新字段"
                ><el-option
                  v-for="column in targetFor(step)?.updateColumns.filter(
                    (c) => !step.keys.includes(c)
                  )"
                  :key="column"
                  :value="column"
                  :label="column" /></el-select
            ></el-form-item>
          </el-form>
          <FieldMappingEditor
            ref="mappingEditors"
            v-model="step.mappings"
            :columns="source.columns || []"
            :fields="
              availableColumns(step).map((name) => ({
                name,
                label: name,
                required: step.keys.includes(name)
              }))
            "
            generic
          />
          <el-collapse>
            <el-collapse-item title="关联查找与高级配置" :name="stepIndex">
              <el-form label-width="120px" @submit.prevent>
                <el-form-item label="条件字段"
                  ><el-input
                    v-model="step.whenField"
                    clearable
                    placeholder="字段非空时执行；留空始终执行"
                /></el-form-item>
                <el-form-item label="最新值时间列"
                  ><el-input
                    v-model="step.newerBy"
                    clearable
                    placeholder="只用更晚时间覆盖目标；留空按同步顺序更新"
                /></el-form-item>
                <el-form-item label="同时间比较列"
                  ><el-input
                    v-model="step.tieBreaker"
                    clearable
                    placeholder="时间相同，取更大数值，例如记录 ID"
                /></el-form-item>
                <el-form-item label="关联查找 JSON"
                  ><el-input
                    :model-value="lookupText(step)"
                    type="textarea"
                    :rows="5"
                    aria-label="关联查找 JSON"
                    @input="(value) => updateLookups(step, value)"
                /></el-form-item>
              </el-form>
              <p
                >关联条件可引用已映射字段，或用 @来源列。前序步骤生成的 ID 可用 $步骤名称.id
                映射。</p
              >
            </el-collapse-item>
          </el-collapse>
        </section>
        <el-button @click="addStep(source)">添加写入步骤</el-button>
        <el-button text type="danger" @click="model.sources.splice(sourceIndex, 1)"
          >删除来源</el-button
        >
      </el-collapse-item>
    </el-collapse>
    <el-button class="mapping-add" @click="addSource">添加来源</el-button>
    <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
  </div>
</template>
<script setup lang="ts">
import * as api from '@/api/pms/integration'
import FieldMappingEditor from './FieldMappingEditor.vue'
const mappingEditors = ref<InstanceType<typeof FieldMappingEditor>[]>([])
const model = defineModel<api.Definition>({ required: true })
const emit = defineEmits<{ templateName: [name: string] }>()
const templates = ref<api.GenericTemplate[]>([])
const targets = ref<api.GenericTarget[]>([])
const templateKey = ref(model.value.taskKey || 'custom')
const openSources = ref([0])
const error = ref('')
const readingSource = ref('')
const loadColumns = async (source: api.Source) => {
  readingSource.value = source.object
  try {
    source.columns = (await api.getColumns(model.value.connectionId, source.table)).map(
      (column) => column.name
    )
  } catch {
    error.value = '来源字段读取失败，请检查连接和表名'
  } finally {
    readingSource.value = ''
  }
}
const lookupDrafts = reactive(new Map<api.GenericTargetStep, string>())

const writableTargets = computed(() => targets.value.filter((t) => t.writable))
const targetFor = (step: api.GenericTargetStep) => targets.value.find((t) => t.table === step.table)
const availableColumns = (step: api.GenericTargetStep) =>
  targetFor(step)?.columns.filter((c) => !(c in (targetFor(step)?.insertDefaults || {}))) || []
const changeTarget = (step: api.GenericTargetStep) => {
  step.keys = [...(targetFor(step)?.keys[0] || [])]
  step.mappings = []
  step.updateColumns = []
  step.lookups = []
}
const addStep = (source: api.Source) => {
  ;(source.targets ||= []).push({
    name: `target${(source.targets?.length || 0) + 1}`,
    table: '',
    keys: [],
    mode: 'UPSERT',
    nullPolicy: 'IGNORE',
    updateColumns: [],
    mappings: [],
    lookups: []
  })
}
const addSource = () => {
  model.value.sources.push({
    object: `SOURCE_${model.value.sources.length + 1}`,
    sourceObject: '',
    readMode: 'TABLE',
    table: '',
    parameters: {},
    sourceKey: 'id',
    columns: [],
    filters: [],
    mappings: [],
    targets: []
  })
  openSources.value.push(model.value.sources.length - 1)
}
const lookupText = (step: api.GenericTargetStep) =>
  lookupDrafts.get(step) ?? JSON.stringify(step.lookups || [], null, 2)
const updateLookups = (step: api.GenericTargetStep, text: string) => {
  lookupDrafts.set(step, text)
}
const loadTemplate = (key: string) => {
  error.value = ''
  lookupDrafts.clear()
  if (key === 'custom') {
    model.value.taskKey = ''
    model.value.sources = []
    addSource()
    return
  }
  const template = templates.value.find((t) => t.key === key)
  if (template) {
    model.value = {
      ...structuredClone(toRaw(template.definition)),
      connectionId: model.value.connectionId
    }
    emit('templateName', template.name)
    openSources.value = [0]
  }
}
const validate = () => {
  error.value = ''
  try {
    for (const [step, text] of lookupDrafts) {
      const value = JSON.parse(text)
      if (!Array.isArray(value)) throw new Error('关联查找必须是数组')
      step.lookups = value
    }
    for (const source of model.value.sources) {
      if (!source.primaryTarget) source.primaryTarget = undefined
      for (const step of source.targets || []) {
        if (!step.whenField) step.whenField = null
        if (!step.newerBy) step.newerBy = null
        if (!step.tieBreaker) step.tieBreaker = null
      }
    }
    if (!mappingEditors.value.every((editor) => editor.validate()))
      throw new Error('请修正字段映射中的 JSON 格式')
    if (!/^[A-Za-z0-9_-]{1,40}$/.test(model.value.taskKey || ''))
      throw new Error('请填写有效的任务标识')
    if (
      !model.value.sources.length ||
      model.value.sources.some(
        (s) => !s.object || !s.sourceObject || !s.sourceKey || !s.targets?.length
      )
    )
      throw new Error('请补齐来源、来源主键和写入步骤')
    return true
  } catch (e) {
    error.value = e instanceof Error ? e.message : '配置格式无效'
    return false
  }
}
onMounted(async () => {
  try {
    ;[templates.value, targets.value] = await Promise.all([
      api.getGenericTemplates(model.value.connectionId),
      api.getGenericTargets()
    ])
  } catch {
    error.value = '模板或目标目录加载失败，请重新打开配置'
  }
})
defineExpose({ validate })
</script>
<style scoped>
.generic-mapping {
  display: grid;
  gap: 16px;
}
.target-step {
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  padding: 16px;
  margin-bottom: 16px;
}
.step-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.mapping-add {
  margin-top: 12px;
  justify-self: start;
}
.generic-mapping :deep(.el-select) {
  width: 100%;
}
</style>
