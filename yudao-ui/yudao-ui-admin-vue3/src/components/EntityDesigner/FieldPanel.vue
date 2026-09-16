<!-- src/components/EntityDesigner/FieldPanel.vue -->
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import type { LowCodeEntity, LowCodeField } from '@/api/lowcode-entity'
import IndexPanel, { type LowCodeIndex } from '@/components/EntityDesigner/IndexPanel.vue'

const props = defineProps<{
  entity: LowCodeEntity
  fields: LowCodeField[]
  indexes?: LowCodeIndex[]
}>()

const emit = defineEmits<{
  'update:entity': [entity: LowCodeEntity]
  'update:fields': [fields: LowCodeField[]]
  'update:indexes': [indexes: LowCodeIndex[]]
}>()

const activeTab = ref<'entity' | 'fields' | 'indexes'>('entity')

const formData = reactive<LowCodeEntity>({ ...props.entity })

watch(() => props.entity, (val) => {
  Object.assign(formData, val)
}, { deep: true })

// 实体属性变更时同步给父组件（el-form 无 change 事件，通过深度 watch formData 触发）
watch(formData, () => {
  emit('update:entity', { ...formData })
}, { deep: true })

/** 索引列表 v-model 代理（透传到 IndexPanel） */
const indexProxy = computed<LowCodeIndex[]>({
  get: () => props.indexes || [],
  set: (v) => emit('update:indexes', v)
})

function addField() {
  const newField: LowCodeField = {
    name: 'new_field',
    label: '新字段',
    fieldType: 'STRING',
    length: 255,
    nullable: 1,
    primaryKey: 0,
    indexed: 0,
    uniqueFlag: 0,
    sortOrder: props.fields.length
  }
  emit('update:fields', [...props.fields, newField])
}

function removeField(index: number) {
  const updated = [...props.fields]
  updated.splice(index, 1)
  emit('update:fields', updated)
}

const FIELD_TYPES = ['STRING', 'INTEGER', 'LONG', 'DECIMAL', 'BOOLEAN', 'DATE', 'DATETIME', 'TEXT']

/** 主键策略可选项 */
const PK_STRATEGIES = [
  { label: 'AUTO_INCREMENT（自增）', value: 'AUTO_INCREMENT' },
  { label: 'UUID（36位）', value: 'UUID' },
  { label: 'SNOWFLAKE（雪花）', value: 'SNOWFLAKE' },
  { label: 'BUSINESS（业务主键）', value: 'BUSINESS' }
] as const
</script>

<template>
  <div class="field-panel">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="实体属性" name="entity">
        <el-form :model="formData" label-width="90px" size="small">
          <el-form-item label="实体编码">
            <el-input v-model="formData.code" placeholder="如 device" />
          </el-form-item>
          <el-form-item label="实体名称">
            <el-input v-model="formData.name" placeholder="如 设备" />
          </el-form-item>
          <el-form-item label="物理表名">
            <el-input v-model="formData.tableName" placeholder="pms_lc_device" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="formData.description" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="业务类型">
            <el-input v-model="formData.bizType" />
          </el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="字段管理" name="fields">
        <el-button type="primary" size="small" @click="addField" style="margin-bottom: 10px">
          新增字段
        </el-button>
        <el-table :data="props.fields" size="small" border>
          <el-table-column label="字段名" prop="name" width="120">
            <template #default="{ row }">
              <el-input v-model="row.name" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="显示名" prop="label" width="120">
            <template #default="{ row }">
              <el-input v-model="row.label" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="类型" prop="fieldType" width="110">
            <template #default="{ row }">
              <el-select v-model="row.fieldType" size="small">
                <el-option v-for="t in FIELD_TYPES" :key="t" :label="t" :value="t" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="长度" prop="length" width="70">
            <template #default="{ row }">
              <el-input-number v-model="row.length" size="small" :min="1" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="精度" prop="scale" width="70">
            <template #default="{ row }">
              <el-input-number
                v-if="row.fieldType === 'DECIMAL'"
                v-model="row.scale"
                size="small"
                :min="0"
                :max="30"
                controls-position="right"
              />
              <span v-else class="col-dash">—</span>
            </template>
          </el-table-column>
          <el-table-column label="默认值" prop="defaultValue" width="120">
            <template #default="{ row }">
              <el-input v-model="row.defaultValue" size="small" placeholder="无" />
            </template>
          </el-table-column>
          <el-table-column label="PK" prop="primaryKey" width="50">
            <template #default="{ row }">
              <el-checkbox v-model="row.primaryKey" :true-value="1" :false-value="0" />
            </template>
          </el-table-column>
          <el-table-column label="主键策略" width="140">
            <template #default="{ row }">
              <el-select
                v-if="row.primaryKey === 1"
                v-model="row.pkStrategy"
                size="small"
                placeholder="选择策略"
                style="width: 100%"
              >
                <el-option
                  v-for="s in PK_STRATEGIES"
                  :key="s.value"
                  :label="s.label"
                  :value="s.value"
                />
              </el-select>
              <span v-else class="col-dash">—</span>
            </template>
          </el-table-column>
          <el-table-column label="可空" prop="nullable" width="50">
            <template #default="{ row }">
              <el-checkbox v-model="row.nullable" :true-value="1" :false-value="0" />
            </template>
          </el-table-column>
          <el-table-column label="索引" prop="indexed" width="55">
            <template #default="{ row }">
              <el-switch v-model="row.indexed" :active-value="1" :inactive-value="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="唯一" prop="uniqueFlag" width="55">
            <template #default="{ row }">
              <el-switch v-model="row.uniqueFlag" :active-value="1" :inactive-value="0" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }">
              <el-button type="danger" size="small" link @click="removeField($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="索引配置" name="indexes">
        <IndexPanel :fields="props.fields" v-model="indexProxy" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script lang="ts">
export default { name: 'FieldPanel' }
</script>

<style scoped lang="scss">
.field-panel {
  padding: 10px;
  height: 100%;
  overflow-y: auto;
  .col-dash {
    color: var(--el-text-color-placeholder);
  }
}
</style>
