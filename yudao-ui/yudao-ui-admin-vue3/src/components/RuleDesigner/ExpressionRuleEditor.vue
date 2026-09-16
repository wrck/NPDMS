<script setup lang="ts">
/**
 * 表达式规则编辑器（包装 ExpressionEditor + facts schema 维护侧栏）。
 *
 * <p>主体复用批次1的 ExpressionEditor（language=aviator，带语法高亮 + 变量/函数补全），
 * 右侧额外提供 inputsSchema 维护面板：用户手动维护输入字段（name/type），
 * 这些字段会作为变量传入 ExpressionEditor 的侧栏供点击插入，并持久化到 rule 的 ext 字段。</p>
 *
 * <p>双向绑定：</p>
 * <ul>
 *   <li>modelValue：Aviator 表达式字符串（与 definition 一致，后端直接求值）</li>
 *   <li>ext：ExpressionExt JSON 字符串（含 inputsSchema）</li>
 * </ul>
 */
import { computed, ref, watch } from 'vue'
import ExpressionEditor from '@/components/ExpressionEditor/index.vue'
import type { ExpressionExt } from '@/api/lowcode-rule'

defineOptions({ name: 'ExpressionRuleEditor' })

const props = defineProps<{
  /** Aviator 表达式字符串（v-model） */
  modelValue: string
  /** ExpressionExt JSON 字符串（v-model:ext） */
  ext?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'update:ext', v: string): void
}>()

/** 表达式文本（v-model 中转） */
const expression = ref(props.modelValue || '')
watch(
  () => props.modelValue,
  (v) => {
    if (v !== expression.value) expression.value = v ?? ''
  }
)
function onExprInput(v: string) {
  expression.value = v
  emit('update:modelValue', v)
}

// ===================== inputsSchema 维护 =====================

/** 解析 ext 为 ExpressionExt，容错处理 */
function parseExt(raw: string | undefined): ExpressionExt {
  if (!raw || !raw.trim()) return { inputsSchema: [] }
  try {
    const obj = JSON.parse(raw) as Partial<ExpressionExt>
    return { inputsSchema: Array.isArray(obj.inputsSchema) ? obj.inputsSchema : [] }
  } catch {
    return { inputsSchema: [] }
  }
}

const inputsSchema = ref(parseExt(props.ext).inputsSchema)

watch(
  () => props.ext,
  (v) => {
    const parsed = parseExt(v)
    // 浅比较避免回写循环
    if (JSON.stringify(parsed.inputsSchema) !== JSON.stringify(inputsSchema.value)) {
      inputsSchema.value = parsed.inputsSchema
    }
  }
)

/** 同步 inputsSchema 到 ext 字符串 */
function syncExt() {
  const payload: ExpressionExt = { inputsSchema: inputsSchema.value.map((s) => ({ ...s })) }
  emit('update:ext', JSON.stringify(payload, null, 2))
}

function addField() {
  inputsSchema.value.push({ name: '', type: 'string' })
  syncExt()
}

function removeField(idx: number) {
  inputsSchema.value.splice(idx, 1)
  syncExt()
}

function onFieldChange() {
  syncExt()
}

/** 传给 ExpressionEditor 的变量列表（{name, type} 形式，支持类型显示） */
const variables = computed(() =>
  inputsSchema.value
    .filter((s) => s.name)
    .map((s) => ({ name: s.name, type: s.type }))
)
</script>

<template>
  <div class="expression-rule-editor">
    <div class="ere-main">
      <!-- 左侧：表达式编辑器（复用 ExpressionEditor，含语法高亮 + 函数库） -->
      <div class="ere-editor">
        <ExpressionEditor
          :model-value="expression"
          language="aviator"
          :variables="variables"
          :height="320"
          @update:model-value="onExprInput"
        />
      </div>

      <!-- 右侧：facts schema 维护侧栏 -->
      <div class="ere-schema">
        <div class="ere-schema-header">
          <span class="ere-schema-title">输入变量 (Facts Schema)</span>
          <el-button size="small" type="primary" link @click="addField">+ 添加</el-button>
        </div>
        <div class="ere-schema-tip">
          手动维护可用变量，点击编辑器变量区可插入到表达式（Aviator 用 ${name}）。
        </div>
        <div class="ere-schema-list">
          <div v-for="(f, idx) in inputsSchema" :key="idx" class="ere-schema-row">
            <el-input
              v-model="f.name"
              size="small"
              placeholder="变量名"
              @input="onFieldChange"
            />
            <el-select
              v-model="f.type"
              size="small"
              class="ere-type-select"
              @change="onFieldChange"
            >
              <el-option label="string" value="string" />
              <el-option label="number" value="number" />
              <el-option label="boolean" value="boolean" />
              <el-option label="date" value="date" />
              <el-option label="object" value="object" />
            </el-select>
            <el-button
              size="small"
              link
              class="ere-row-del"
              title="删除"
              @click="removeField(idx)"
            >✕</el-button>
          </div>
          <div v-if="inputsSchema.length === 0" class="ere-schema-empty">
            暂无变量，点击「+ 添加」
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.expression-rule-editor {
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  overflow: hidden;
  background: var(--el-bg-color);
}

.ere-main {
  display: flex;
  min-height: 320px;
}

.ere-editor {
  flex: 1;
  min-width: 0;
  display: flex;
}

/* ExpressionEditor 自身有边框，这里去掉外层重复边框 */
.ere-editor :deep(.expression-editor) {
  border: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  width: 100%;
}

.ere-schema {
  width: 240px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--el-fill-color-blank);
}

.ere-schema-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-light);
}

.ere-schema-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.ere-schema-tip {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  line-height: 1.5;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.ere-schema-list {
  flex: 1;
  overflow: auto;
  padding: 6px;
}

.ere-schema-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 6px;
}

.ere-type-select {
  width: 92px;
  flex-shrink: 0;
}

.ere-row-del {
  color: var(--el-color-danger);
  flex-shrink: 0;
}

.ere-schema-empty {
  text-align: center;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  padding: 20px 0;
}
</style>
