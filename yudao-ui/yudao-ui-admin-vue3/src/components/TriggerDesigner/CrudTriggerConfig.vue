<script setup lang="ts">
/**
 * CRUD 触发配置（借鉴 ServiceNow Flow Designer 的 Record Trigger）。
 *
 * <p>配置项：</p>
 * <ul>
 *   <li>实体选择：从实体列表加载（getEntityList API）</li>
 *   <li>操作多选：CREATE / UPDATE / DELETE</li>
 *   <li>时机多选：BEFORE / AFTER</li>
 *   <li>条件表达式（可选）：Groovy 表达式，作为触发前置过滤条件</li>
 * </ul>
 *
 * <p>注：后端 CrudTriggerExecutor.matches 目前不支持 condition，本轮前端先存入
 * config.condition 字段，后端可选实现。</p>
 */
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ExpressionEditor from '@/components/ExpressionEditor/index.vue'
import { getEntityList, type LowCodeEntity } from '@/api/lowcode-entity'
import type { CrudOperation, CrudTiming, CrudTriggerConfig } from '@/api/lowcode-trigger'

defineOptions({ name: 'CrudTriggerConfigView' })

const props = defineProps<{
  /** 结构化 CRUD 配置（v-model） */
  modelValue: CrudTriggerConfig
}>()

const emit = defineEmits<{ (e: 'update:modelValue', v: CrudTriggerConfig): void }>()

const operationOptions: Array<{ label: string; value: CrudOperation }> = [
  { label: '新增 (CREATE)', value: 'CREATE' },
  { label: '更新 (UPDATE)', value: 'UPDATE' },
  { label: '删除 (DELETE)', value: 'DELETE' }
]

const timingOptions: Array<{ label: string; value: CrudTiming }> = [
  { label: '执行前 (BEFORE)', value: 'BEFORE' },
  { label: '执行后 (AFTER)', value: 'AFTER' }
]

/** 条件表达式可用变量提示（Groovy 语言，裸名引用） */
const conditionVariables = ['entity', 'operation', 'record', 'oldRecord', 'user']

const entities = ref<LowCodeEntity[]>([])
const loadingEntities = ref(false)

const form = reactive<CrudTriggerConfig>({
  entityCode: props.modelValue.entityCode || '',
  operations: [...(props.modelValue.operations || [])],
  timing: [...(props.modelValue.timing || [])],
  condition: props.modelValue.condition ?? ''
})

// 外部变更同步到本地（深拷贝数组避免双向引用）
watch(
  () => props.modelValue,
  (v) => {
    form.entityCode = v.entityCode || ''
    form.operations = [...(v.operations || [])]
    form.timing = [...(v.timing || [])]
    form.condition = v.condition ?? ''
  }
)

// 本地变更向上同步
watch(
  form,
  () => {
    emit('update:modelValue', {
      entityCode: form.entityCode,
      operations: [...form.operations],
      timing: [...form.timing],
      condition: form.condition
    })
  },
  { deep: true }
)

async function loadEntities() {
  loadingEntities.value = true
  try {
    entities.value = await getEntityList()
  } catch (e: unknown) {
    // 实体列表加载失败时降级为空，用户仍可手动输入
    entities.value = []
    const msg = e instanceof Error ? e.message : String(e)
    ElMessage.warning('实体列表加载失败：' + msg)
  } finally {
    loadingEntities.value = false
  }
}

onMounted(loadEntities)
</script>

<template>
  <el-form :model="form" label-width="100px" class="crud-trigger-config">
    <el-form-item label="触发实体" required>
      <el-select
        v-model="form.entityCode"
        filterable
        allow-create
        default-first-option
        :loading="loadingEntities"
        placeholder="选择或输入实体编码"
        style="width: 100%"
      >
        <el-option
          v-for="e in entities"
          :key="e.code"
          :label="`${e.name} (${e.code})`"
          :value="e.code"
        />
      </el-select>
      <div class="field-hint">从实体列表加载，如列表为空可手动输入实体编码</div>
    </el-form-item>

    <el-form-item label="触发操作" required>
      <el-checkbox-group v-model="form.operations">
        <el-checkbox
          v-for="o in operationOptions"
          :key="o.value"
          :value="o.value"
          :label="o.label"
        />
      </el-checkbox-group>
    </el-form-item>

    <el-form-item label="触发时机" required>
      <el-checkbox-group v-model="form.timing">
        <el-checkbox
          v-for="t in timingOptions"
          :key="t.value"
          :value="t.value"
          :label="t.label"
        />
      </el-checkbox-group>
    </el-form-item>

    <el-form-item label="条件表达式">
      <div class="condition-wrap">
        <ExpressionEditor
          :model-value="form.condition || ''"
          @update:model-value="(value: string) => (form.condition = value)"
          language="groovy"
          :variables="conditionVariables"
          :height="160"
        />
        <div class="field-hint">
          可选。Groovy 表达式，返回 true 才触发。变量：entity / operation / record / oldRecord / user
          （后端 CrudTriggerExecutor.matches 当前未支持 condition，前端先存入，后端可选实现）
        </div>
      </div>
    </el-form-item>
  </el-form>
</template>

<style scoped lang="scss">
.crud-trigger-config {
  max-width: 720px;
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.condition-wrap {
  width: 100%;
}
</style>
