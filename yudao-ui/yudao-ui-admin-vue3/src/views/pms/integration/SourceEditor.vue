<template>
  <el-tabs v-model="expanded" type="card">
    <el-tab-pane
      v-for="source in model.sources"
      :key="source.object"
      :name="source.object"
      :label="objectLabel(source.object)"
    >
      <el-form label-width="128px" class="integration-form" @submit.prevent>
        <el-alert
          v-if="parameterErrors.has(source) || source.filters.some((f) => filterErrors.has(f))"
          title="参数或筛选 JSON 无效，请修正后保存。"
          type="error"
          :closable="false"
        />
        <el-form-item label="稳定来源对象标识"
          ><el-input v-model="source.sourceObject"
        /></el-form-item>
        <el-form-item label="读取方式"
          ><el-select v-model="source.readMode"
            ><el-option label="表或视图" value="TABLE" /><el-option
              label="只读 SQL"
              value="SQL" /></el-select
        ></el-form-item>
        <el-form-item label="源主键列"><el-input v-model="source.sourceKey" /></el-form-item>
        <el-form-item label="同步源主键">
          <el-switch
            v-model="source.syncPrimaryKey"
            :disabled="
              !adapter.objects.find((o) => o.name === source.object)?.supportsSourcePrimaryKey
            "
            :aria-label="`${objectLabel(source.object)}同步源主键`"
            active-text="目标主键与源主键一致"
          />
        </el-form-item>
        <el-alert
          v-if="source.syncPrimaryKey"
          title="源主键须为正整数 Long；主键冲突整批失败，不自动换号。已有映射不一致时，可在加载策略中选择仅重置映射后追加更新，或截断目标重建。仅重置映射不修改旧目标主键，编码冲突仍会拒绝。"
          type="warning"
          :closable="false"
          class="mb-4"
        />
        <el-form-item v-if="source.readMode === 'TABLE'" label="来源表或视图">
          <div class="integration-source-select">
            <el-select
              v-model="source.table"
              filterable
              allow-create
              default-first-option
              @change="loadColumns(source)"
            >
              <el-option
                v-for="table in tables"
                :key="table.name"
                :label="table.name"
                :value="table.name"
              />
            </el-select>
            <el-button @click="loadColumns(source)">读取字段</el-button>
          </div>
        </el-form-item>
        <template v-else>
          <el-alert
            title="仅允许单条只读 SELECT；增量窗口由系统统一添加。修改需要 SQL 配置权限。"
            type="info"
            :closable="false"
            class="mb-4"
          />
          <el-form-item label="只读 SQL"
            ><el-input
              v-model="source.sql"
              type="textarea"
              :rows="5"
              placeholder="SELECT id, name, updated_at FROM source_table WHERE category = :category"
          /></el-form-item>
          <el-form-item label="命名参数（JSON）"
            ><el-input
              :model-value="parameterDrafts.get(source) ?? JSON.stringify(source.parameters || {})"
              @update:model-value="parseParameters(source, $event)"
          /></el-form-item>
        </template>
        <el-form-item v-if="model.mode === 'INCREMENTAL'" label="来源更新时间列"
          ><el-input v-model="source.updatedAt" placeholder="例如 updated_at，必须包含在查询输出中"
        /></el-form-item>
        <template v-if="source.readMode === 'TABLE'">
          <el-form-item label="读取字段"
            ><el-select
              v-model="source.columns"
              multiple
              filterable
              allow-create
              default-first-option
              ><el-option
                v-for="column in columns[source.object] || source.columns"
                :key="column"
                :value="column"
                :label="column" /></el-select
          ></el-form-item>
          <el-form-item label="筛选条件">
            <div v-for="(filter, index) in source.filters" :key="index" class="integration-filters">
              <el-input
                v-model="filter.column"
                class="!w-40"
                aria-label="筛选字段"
                placeholder="字段"
              />
              <el-select v-model="filter.operator" class="!w-36" aria-label="筛选操作符"
                ><el-option v-for="op in operators" :key="op" :value="op" :label="op"
              /></el-select>
              <el-input
                :model-value="
                  filterDrafts.get(filter) ??
                  (typeof filter.value === 'string' ? filter.value : JSON.stringify(filter.value))
                "
                class="!w-60"
                aria-label="筛选值"
                placeholder="IN 输入 JSON 数组"
                @update:model-value="setFilter(filter, $event)"
              />
              <el-button @click="source.filters.splice(index, 1)">移除筛选</el-button>
            </div>
            <el-button @click="source.filters.push({ column: '', operator: '=', value: '' })"
              ><Icon icon="ep:plus" class="mr-5px" />添加筛选</el-button
            >
          </el-form-item>
        </template>
      </el-form>
      <div class="integration-section-title">字段映射</div>
      <FieldMappingEditor
        ref="mappingEditors"
        v-model="source.mappings"
        :columns="columns[source.object] || source.columns"
        :fields="adapter.objects.find((o) => o.name === source.object)?.fields || []"
      />
    </el-tab-pane>
  </el-tabs>
</template>
<script setup lang="ts">
import * as api from '@/api/pms/integration'
import FieldMappingEditor from './FieldMappingEditor.vue'
const model = defineModel<api.Definition>({ required: true })
const props = defineProps<{ adapter: api.Adapter }>()
// SQL sources omit table-only fields; initialize them before rendering or switching modes.
watchEffect(() => {
  for (const source of model.value.sources) {
    source.filters ??= []
    source.columns ??= []
  }
})
const message = useMessage()
const parameterErrors = reactive(new Set<api.Source>())
const parameterDrafts = reactive(new Map<api.Source, string>())
const filterDrafts = reactive(new Map<api.Source['filters'][number], string>())
const filterErrors = reactive(new Set<api.Source['filters'][number]>())
const mappingEditors = ref<InstanceType<typeof FieldMappingEditor>[]>([])
const validate = () => {
  const valid =
    model.value.sources.every((s) =>
      s.readMode === 'SQL'
        ? !parameterErrors.has(s)
        : !s.filters.some((f) => f.operator === 'IN' && filterErrors.has(f))
    ) && mappingEditors.value.every((editor) => editor.validate())
  if (!valid) message.error('请先修正来源参数、筛选或枚举映射中的 JSON 错误')
  return valid
}
defineExpose({ validate })
const expanded = ref(model.value.sources[0]?.object || '')
const tables = ref<{ name: string; type: string }[]>([])
const columns = reactive<Record<string, string[]>>({})
const operators = ['=', '<>', '>', '>=', '<', '<=', 'LIKE', 'IN', 'IS NULL', 'IS NOT NULL']
const objectLabel = (name: string) =>
  props.adapter.objects.find((o) => o.name === name)?.label || name
const loadColumns = async (source: api.Source) => {
  columns[source.object] = (await api.getColumns(model.value.connectionId, source.table)).map(
    (c) => c.name
  )
}
const parseParameters = (s: api.Source, value: string) => {
  parameterDrafts.set(s, value)
  try {
    const data = JSON.parse(value)
    if (!data || typeof data !== 'object' || Array.isArray(data)) throw new Error()
    s.parameters = data
    parameterErrors.delete(s)
  } catch {
    parameterErrors.add(s)
  }
}
const setFilter = (filter: api.Source['filters'][number], value: string) => {
  filterDrafts.set(filter, value)
  try {
    const parsed = filter.operator === 'IN' ? JSON.parse(value) : value
    if (filter.operator === 'IN' && !Array.isArray(parsed)) throw new Error()
    filter.value = parsed
    filterErrors.delete(filter)
  } catch {
    filterErrors.add(filter)
  }
}
onMounted(async () => {
  tables.value = await api.getTables(model.value.connectionId)
})
</script>
