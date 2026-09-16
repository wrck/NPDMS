<script setup lang="ts">
/**
 * 单个属性字段的递归渲染组件（schema 驱动）。
 *
 * <p>根据 {@link ComponentPropDef.type} 渲染对应的 Element Plus 控件；
 * type=object 时按 properties 递归渲染子属性，type=array 时按 itemProp
 * 递归渲染数组项并支持新增/删除。组件通过自身文件名（PropField）实现递归引用。</p>
 *
 * <p>type=code / type=expression 复用批次1的 ExpressionEditor（textarea + 变量/字段
 * 侧栏 + 函数库 + 简易高亮）；expression 额外提供 language 切换下拉。</p>
 *
 * <p>所有变更通过 emit('update:modelValue') 上抛不可变新值，
 * 由父级（LowCodePropertyPanel）写回响应式 modelValue，保证单向数据流。</p>
 */
import { ref } from 'vue'
import type { ComponentPropDef } from '@/components/LowCodeComponentRegistry/types'
import ExpressionEditor from '@/components/ExpressionEditor/index.vue'

defineOptions({ name: 'PropField' })

const props = defineProps<{
  propDef: ComponentPropDef
  modelValue: any
}>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()

/** expression 类型的语言切换状态（缺省取 schema.language，回退 aviator） */
const expressionLang = ref<'groovy' | 'aviator' | 'javascript'>(
  props.propDef.language || 'aviator'
)

/** 显示标签：优先 label，回退 key */
function label(): string {
  return props.propDef.label || props.propDef.key
}

function resolveDateType(): 'date' | 'datetime' | 'daterange' {
  const type = props.propDef.dateType
  return type === 'datetime' || type === 'daterange' ? type : 'date'
}

/** 容器值的安全读取：object 缺省返回 {}，array 缺省返回 [] */
function containerValue(): any {
  if (props.propDef.type === 'object') {
    return props.modelValue && typeof props.modelValue === 'object' ? props.modelValue : {}
  }
  if (props.propDef.type === 'array') {
    return Array.isArray(props.modelValue) ? props.modelValue : []
  }
  return props.modelValue
}

/** 数组项 schema：优先 itemProp，缺省按 string 渲染 */
function itemSchema(): ComponentPropDef {
  return props.propDef.itemProp || { key: 'item', type: 'string' }
}

/** 根据类型生成默认值（用于数组新增项初始化） */
function defaultForType(type: ComponentPropDef['type']): any {
  switch (type) {
    case 'boolean':
      return false
    case 'number':
      return 0
    case 'object':
      return {}
    case 'array':
      return []
    default:
      return ''
  }
}

/** 数组：新增一项 */
function addArrayItem() {
  const arr = containerValue() as any[]
  const itemDef = itemSchema()
  const newItem = itemDef.default !== undefined ? itemDef.default : defaultForType(itemDef.type)
  emit('update:modelValue', [...arr, newItem])
}

/** 数组：删除指定索引项 */
function removeArrayItem(idx: number) {
  const arr = containerValue() as any[]
  emit('update:modelValue', arr.filter((_, i) => i !== idx))
}

/** object 子属性变更：emit 新对象 */
function updateObjectField(key: string, v: any) {
  const obj = containerValue() as Record<string, any>
  emit('update:modelValue', { ...obj, [key]: v })
}

/** 数组项变更：emit 新数组 */
function updateArrayItem(idx: number, v: any) {
  const arr = containerValue() as any[]
  emit('update:modelValue', arr.map((it, i) => (i === idx ? v : it)))
}
</script>

<template>
  <el-form-item class="prop-field">
    <template #label>
      <span class="prop-label">
        <span v-if="propDef.required" class="prop-required">*</span>
        {{ label() }}
        <el-tooltip v-if="propDef.description" :content="propDef.description" placement="top">
          <el-icon class="prop-tip-icon"><QuestionFilled /></el-icon>
        </el-tooltip>
      </span>
    </template>

    <!-- 布尔 -->
    <el-switch
      v-if="propDef.type === 'boolean'"
      :model-value="!!modelValue"
      @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    />

    <!-- 数字 -->
    <el-input-number
      v-else-if="propDef.type === 'number'"
      :model-value="modelValue"
      :min="propDef.min"
      :max="propDef.max"
      :step="propDef.step ?? 1"
      controls-position="right"
      style="width: 100%"
      @update:model-value="(v: number) => emit('update:modelValue', v)"
    />

    <!-- 下拉选择 -->
    <el-select
      v-else-if="propDef.type === 'select'"
      :model-value="modelValue"
      :placeholder="propDef.placeholder"
      style="width: 100%"
      @update:model-value="(v: any) => emit('update:modelValue', v)"
    >
      <el-option
        v-for="opt in propDef.options || []"
        :key="String(opt.value)"
        :label="opt.label"
        :value="opt.value"
      />
    </el-select>

    <!-- 颜色 -->
    <el-color-picker
      v-else-if="propDef.type === 'color'"
      :model-value="modelValue"
      @update:model-value="(v: string) => emit('update:modelValue', v)"
    />

    <!-- 日期 -->
    <el-date-picker
      v-else-if="propDef.type === 'date'"
      :model-value="modelValue"
      :type="resolveDateType()"
      :placeholder="propDef.placeholder"
      style="width: 100%"
      @update:model-value="(v: any) => emit('update:modelValue', v)"
    />

    <!-- 代码：复用 ExpressionEditor（textarea + 高亮 + 函数库） -->
    <ExpressionEditor
      v-else-if="propDef.type === 'code'"
      :model-value="modelValue || ''"
      :language="propDef.language || 'aviator'"
      :height="propDef.rows ? propDef.rows * 22 : 160"
      @update:model-value="(v: string) => emit('update:modelValue', v)"
    />

    <!-- 表达式：ExpressionEditor + language 切换下拉 -->
    <div v-else-if="propDef.type === 'expression'" class="prop-expression">
      <el-select
        v-model="expressionLang"
        size="small"
        class="prop-expression-lang"
      >
        <el-option label="Aviator" value="aviator" />
        <el-option label="Groovy" value="groovy" />
        <el-option label="JavaScript" value="javascript" />
      </el-select>
      <ExpressionEditor
        :model-value="modelValue || ''"
        :language="expressionLang"
        :height="propDef.rows ? propDef.rows * 22 : 140"
        @update:model-value="(v: string) => emit('update:modelValue', v)"
      />
    </div>

    <!-- 对象：折叠面板 + 递归渲染子属性 -->
    <div v-else-if="propDef.type === 'object'" class="prop-object">
      <el-collapse>
        <el-collapse-item title="展开配置" name="props">
          <PropField
            v-for="sub in propDef.properties || []"
            :key="sub.key"
            :prop-def="sub"
            :model-value="(containerValue() as Record<string, any>)[sub.key]"
            @update:model-value="(v: any) => updateObjectField(sub.key, v)"
          />
        </el-collapse-item>
      </el-collapse>
    </div>

    <!-- 数组：动态列表 + 递归渲染单项 -->
    <div v-else-if="propDef.type === 'array'" class="prop-array">
      <div
        v-for="(item, idx) in (containerValue() as any[])"
        :key="idx"
        class="prop-array-item"
      >
        <PropField
          :prop-def="itemSchema()"
          :model-value="item"
          @update:model-value="(v: any) => updateArrayItem(idx, v)"
        />
        <el-button
          :icon="'Delete'"
          type="danger"
          size="small"
          class="prop-array-remove"
          @click="removeArrayItem(idx)"
        />
      </div>
      <el-button :icon="'Plus'" size="small" @click="addArrayItem">新增一项</el-button>
    </div>

    <!-- 默认：字符串 -->
    <el-input
      v-else
      :model-value="modelValue"
      :placeholder="propDef.placeholder || (propDef.default !== undefined ? `默认: ${propDef.default}` : '')"
      @update:model-value="(v: string) => emit('update:modelValue', v)"
    />
  </el-form-item>
</template>

<style scoped>
.prop-field {
  margin-bottom: 12px;
}
.prop-label {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}
.prop-required {
  color: var(--el-color-danger);
  margin-right: 2px;
}
.prop-tip-icon {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  cursor: help;
}
.prop-object,
.prop-array {
  width: 100%;
}
.prop-expression {
  width: 100%;
}
.prop-expression-lang {
  width: 120px;
  margin-bottom: 6px;
}
.prop-object :deep(.el-collapse-item__header),
.prop-array :deep(.el-collapse-item__header) {
  font-size: 13px;
}
.prop-array-item {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  margin-bottom: 8px;
  padding: 6px;
  border: 1px dashed var(--el-border-color-lighter);
  border-radius: 4px;
}
.prop-array-item :deep(.el-form-item) {
  flex: 1;
  margin-bottom: 0;
}
.prop-array-remove {
  flex-shrink: 0;
  margin-top: 2px;
}
</style>
