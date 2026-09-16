<!-- src/components/EntityDesigner/IndexPanel.vue -->
<script setup lang="ts">
/**
 * 索引配置面板。
 *
 * <p>展示实体的复合索引列表，支持新增/编辑/删除索引。
 * 每个索引包含：索引名 + 多字段选择（el-select multiple）+ 是否唯一。
 * 字段列表从父级 FieldPanel 传入（当前实体的所有字段）。</p>
 *
 * <p>索引数据通过 v-model（indexes）双向绑定，由 entity-designer 持有；
 * 当前为本地维护（内存态），后续可扩展到后端持久化。</p>
 */
import { computed, ref } from 'vue'
import type { LowCodeField } from '@/api/lowcode-entity'

export interface LowCodeIndex {
  id?: number
  /** 索引名 */
  name: string
  /** 索引字段名列表（对应 LowCodeField.name） */
  fields: string[]
  /** 是否唯一索引 */
  unique: boolean
}

const props = defineProps<{
  fields: LowCodeField[]
  modelValue: LowCodeIndex[]
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: LowCodeIndex[]): void }>()

/** 可选字段选项（字段名 + 类型提示） */
const fieldOptions = computed(() =>
  props.fields.map((f) => ({ label: `${f.name} (${f.fieldType})`, value: f.name }))
)

/** 内部可编辑副本（与 modelValue 同步） */
const indexes = computed<LowCodeIndex[]>({
  get: () => props.modelValue || [],
  set: (v) => emit('update:modelValue', v)
})

/** 当前编辑中的索引（新增/编辑共用） */
const editing = ref<LowCodeIndex | null>(null)
const editingIndex = ref<number>(-1) // -1 表示新增模式

function startAdd() {
  editing.value = { name: '', fields: [], unique: false }
  editingIndex.value = -1
}

function startEdit(idx: number) {
  const src = indexes.value[idx]
  editing.value = { ...src, fields: [...src.fields] }
  editingIndex.value = idx
}

function cancelEdit() {
  editing.value = null
  editingIndex.value = -1
}

function saveEdit() {
  if (!editing.value) return
  if (!editing.value.name.trim()) {
    return
  }
  if (editing.value.fields.length === 0) {
    return
  }
  const list = [...indexes.value]
  if (editingIndex.value >= 0) {
    list[editingIndex.value] = { ...editing.value }
  } else {
    list.push({ ...editing.value })
  }
  indexes.value = list
  cancelEdit()
}

function removeIndex(idx: number) {
  const list = [...indexes.value]
  list.splice(idx, 1)
  indexes.value = list
}
</script>

<template>
  <div class="index-panel">
    <div class="panel-toolbar">
      <el-button type="primary" size="small" :icon="'Plus'" @click="startAdd" :disabled="!!editing">
        新增索引
      </el-button>
      <span v-if="fields.length === 0" class="panel-tip">请先添加字段</span>
    </div>

    <!-- 索引列表 -->
    <el-table :data="indexes" size="small" border empty-text="暂无索引">
      <el-table-column label="索引名" prop="name" min-width="120" />
      <el-table-column label="字段" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="f in row.fields" :key="f" size="small" type="info" class="idx-field-tag">{{ f }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="唯一" width="60" align="center">
        <template #default="{ row }">
          <el-tag :type="row.unique ? 'danger' : 'info'" size="small">
            {{ row.unique ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" align="center">
        <template #default="{ $index }">
          <el-button size="small" link type="primary" @click="startEdit($index)" :disabled="!!editing">编辑</el-button>
          <el-button size="small" link type="danger" @click="removeIndex($index)" :disabled="!!editing">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑表单 -->
    <div v-if="editing" class="index-edit-form">
      <el-form :model="editing" label-width="80px" size="small">
        <el-form-item label="索引名" required>
          <el-input v-model="editing.name" placeholder="如 idx_user_status" />
        </el-form-item>
        <el-form-item label="字段" required>
          <el-select
            v-model="editing.fields"
            multiple
            filterable
            placeholder="选择索引字段（可多选）"
            style="width: 100%"
          >
            <el-option
              v-for="opt in fieldOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="唯一索引">
          <el-switch v-model="editing.unique" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="small" @click="saveEdit">保存</el-button>
          <el-button size="small" @click="cancelEdit">取消</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'IndexPanel' }
</script>

<style scoped lang="scss">
.index-panel {
  .panel-toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }
  .panel-tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
  .idx-field-tag {
    margin-right: 4px;
    margin-bottom: 2px;
  }
  .index-edit-form {
    margin-top: 12px;
    padding: 10px;
    border: 1px dashed var(--el-border-color);
    border-radius: 4px;
    background: var(--el-fill-color-light);
  }
}
</style>
