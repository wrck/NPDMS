<script setup lang="ts">
/**
 * 事件触发配置（EventBus 事件名）。
 *
 * <p>配置项：</p>
 * <ul>
 *   <li>事件名：可从预置事件下拉选择或手动输入（如 entity.created / form.submitted）</li>
 *   <li>事件 payload schema（可选）：字段名 + 类型的表格编辑器</li>
 * </ul>
 */
import { reactive, watch } from 'vue'
import type { EventTriggerConfig } from '@/api/lowcode-trigger'

defineOptions({ name: 'EventTriggerConfigView' })

const props = defineProps<{
  /** 结构化事件配置（v-model） */
  modelValue: EventTriggerConfig
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: EventTriggerConfig): void }>()

/** 预置事件名（可扩展） */
const presetEvents = [
  'entity.created',
  'entity.updated',
  'entity.deleted',
  'form.submitted',
  'form.approved',
  'workflow.started',
  'workflow.completed',
  'connector.called'
]

/** payload 字段类型选项 */
const typeOptions = [
  'STRING',
  'INTEGER',
  'LONG',
  'DECIMAL',
  'BOOLEAN',
  'DATE',
  'DATETIME',
  'OBJECT',
  'ARRAY'
]

const form = reactive<EventTriggerConfig>({
  eventName: props.modelValue.eventName || '',
  payloadSchema: (props.modelValue.payloadSchema || []).map((f) => ({ ...f }))
})

// 外部变更同步到本地
watch(
  () => props.modelValue,
  (v) => {
    form.eventName = v.eventName || ''
    form.payloadSchema = (v.payloadSchema || []).map((f) => ({ ...f }))
  }
)

// 本地变更向上同步
watch(
  form,
  () => {
    emit('update:modelValue', {
      eventName: form.eventName,
      payloadSchema: (form.payloadSchema || []).map((f) => ({ ...f }))
    })
  },
  { deep: true }
)

function addField() {
  if (!form.payloadSchema) form.payloadSchema = []
  form.payloadSchema.push({ name: '', type: 'STRING' })
}

function removeField(index: number) {
  if (!form.payloadSchema) return
  form.payloadSchema.splice(index, 1)
}
</script>

<template>
  <el-form :model="form" label-width="100px" class="event-trigger-config">
    <el-form-item label="事件名" required>
      <el-select
        v-model="form.eventName"
        filterable
        allow-create
        default-first-option
        placeholder="选择或输入事件名（如 entity.created）"
        style="width: 100%"
      >
        <el-option v-for="e in presetEvents" :key="e" :label="e" :value="e" />
      </el-select>
      <div class="field-hint">EventBus 事件名，发布方与订阅方需保持一致</div>
    </el-form-item>

    <el-form-item label="Payload Schema">
      <div class="schema-wrap">
        <el-table :data="form.payloadSchema" border size="small" empty-text="暂无字段">
          <el-table-column label="字段名" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.name" placeholder="如 userId" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="类型" width="160">
            <template #default="{ row }">
              <el-select v-model="row.type" size="small">
                <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="{ $index }">
              <el-button size="small" type="danger" link @click="removeField($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-button size="small" style="margin-top: 8px" @click="addField">+ 新增字段</el-button>
        <div class="field-hint">可选。定义事件 payload 的字段结构，便于目标微流/流程接收时校验</div>
      </div>
    </el-form-item>
  </el-form>
</template>

<style scoped lang="scss">
.event-trigger-config {
  max-width: 720px;
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.schema-wrap {
  width: 100%;
}
</style>
