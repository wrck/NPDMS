<script setup lang="ts">
/**
 * 低代码表单渲染引擎。
 *
 * <p>根据传入的 {@link FormConfig} 动态渲染 Element Plus 表单，支持：
 * <ul>
 *   <li>grid / tabs / collapse 三种布局</li>
 *   <li>v-model 双向绑定到 modelValue</li>
 *   <li>el-form rules 校验（自动合并 required）</li>
 *   <li>disabled / readonly / hidden 字段</li>
 *   <li>18 种字段类型 + 自定义组件（type=custom 通过 componentRegistry 解析）</li>
 *   <li>change 事件回调（emit + props.eventHandlers）</li>
 * </ul>
 * </p>
 */
import { computed, markRaw, reactive, ref, watch } from 'vue'
import {
  ElCascader,
  ElCheckboxGroup,
  ElDatePicker,
  ElDivider,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElRadioGroup,
  ElRate,
  ElSelect,
  ElSlider,
  ElSwitch,
  ElUpload,
  type FormInstance,
  type FormRules
} from 'element-plus'
import type { Component } from 'vue'
import {
  FieldType,
  LayoutType,
  type FormConfig,
  type FormFieldConfig,
  type ResponsiveSpan
} from '@/api/lowcode'

/** Props 定义 */
const props = withDefaults(
  defineProps<{
    /** 表单配置（解析后的 FormConfig 对象） */
    config: FormConfig
    /** 表单数据对象（v-model） */
    modelValue?: Record<string, unknown>
    /** 是否禁用整个表单 */
    disabled?: boolean
    /** 自定义组件注册表：key 为 props.componentName，value 为组件定义 */
    componentRegistry?: Record<string, Component>
    /** 事件处理器映射：key 为 field.events.change 值，value 为回调函数 */
    eventHandlers?: Record<string, (...args: unknown[]) => void>
  }>(),
  {
    disabled: false,
    componentRegistry: () => ({}),
    eventHandlers: () => ({})
  }
)

/** Emits 定义 */
const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, unknown>): void
  (e: 'submit', value: Record<string, unknown>): void
  (e: 'validate-fail', errors: unknown): void
  (e: 'field-change', field: FormFieldConfig, value: unknown): void
}>()

/** 表单 ref */
const formRef = ref<FormInstance>()

/**
 * 内部维护的表单数据（响应式）。
 *
 * <p>值为 any 类型：动态表单字段的实际类型由运行时字段类型决定
 * （input → string、number → number、checkbox → array 等），
 * 使用 any 以兼容所有 Element Plus 组件的 v-model 类型签名。</p>
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const formData = reactive<Record<string, any>>({ ...(props.modelValue || {}) })

/**
 * 初始化字段默认值：将 config.fields 中的 defaultValue 写入未提供的字段。
 */
function initDefaults() {
  for (const field of props.config.fields || []) {
    if (!(field.prop in formData)) {
      if (field.defaultValue !== undefined && field.defaultValue !== null) {
        formData[field.prop] = field.defaultValue
      } else if (field.type === FieldType.CHECKBOX) {
        formData[field.prop] = []
      } else {
        formData[field.prop] = ''
      }
    }
  }
}

// 监听 config 变化时重新初始化默认值
watch(
  () => props.config,
  () => initDefaults(),
  { immediate: true, deep: false }
)

// 监听外部 modelValue 变化，同步到内部 formData
watch(
  () => props.modelValue,
  (val) => {
    if (!val) return
    let changed = false
    for (const key of Object.keys(val)) {
      if (formData[key] !== val[key]) {
        formData[key] = val[key]
        changed = true
      }
    }
    if (changed) {
      // 不在此处回传，避免循环
    }
  },
  { deep: true }
)

// 内部数据变化时回传父组件
watch(
  formData,
  (val) => {
    emit('update:modelValue', { ...val })
  },
  { deep: true }
)

/** 可见字段（过滤 hidden=true） */
const visibleFields = computed(() =>
  (props.config.fields || []).filter((f) => !f.hidden)
)

/**
 * 生成 el-form rules：将 field.required 合并为 required 规则，并合并自定义 rules。
 */
const formRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  for (const field of props.config.fields || []) {
    const list: Array<Record<string, unknown>> = []
    if (field.required) {
      list.push({
        required: true,
        message: field.placeholder || `请填写${field.label}`,
        trigger: ['blur', 'change']
      })
    }
    if (field.rules && Array.isArray(field.rules)) {
      for (const r of field.rules) {
        list.push({ ...r })
      }
    }
    if (list.length > 0) {
      rules[field.prop] = list
    }
  }
  return rules
})

/** 布局配置 */
const layout = computed(() => props.config.layout || { type: LayoutType.GRID, gutter: 16 })

/**
 * 解析栅格 span 为 el-col 绑定属性。
 *
 * <p>向后兼容：span 为数字或缺省时按 :span= 渲染（缺省 24）；
 * span 为响应式断点对象时按 :xs= :sm= :md= :lg= :xl= 渲染。</p>
 */
function colProps(span: number | ResponsiveSpan | undefined): Record<string, number> {
  if (span === undefined || typeof span === 'number') {
    return { span: span ?? 24 }
  }
  const result: Record<string, number> = {}
  if (span.xs !== undefined) result.xs = span.xs
  if (span.sm !== undefined) result.sm = span.sm
  if (span.md !== undefined) result.md = span.md
  if (span.lg !== undefined) result.lg = span.lg
  if (span.xl !== undefined) result.xl = span.xl
  return result
}

/** tabs 折叠面板激活项 */
const activeTab = ref<string>('')
const activeCollapse = ref<string[]>([])

/** 初始化 tabs/collapse 默认激活第一项 */
watch(
  layout,
  (val) => {
    if (val.type === LayoutType.TABS && val.tabs && val.tabs.length > 0 && !activeTab.value) {
      activeTab.value = val.tabs[0].name || val.tabs[0].title
    }
    if (val.type === LayoutType.COLLAPSE && val.collapse && val.collapse.length > 0) {
      activeCollapse.value = val.collapse.map((c, i) => c.name || String(i))
    }
  },
  { immediate: true }
)

/**
 * 将字段 id 列表转为字段对象列表。
 */
function resolveFields(ids: string[]): FormFieldConfig[] {
  const map = new Map<string, FormFieldConfig>()
  for (const f of props.config.fields || []) {
    map.set(f.id, f)
  }
  return ids.map((id) => map.get(id)).filter((f): f is FormFieldConfig => !!f && !f.hidden)
}

/**
 * 解析字段对应的渲染组件。
 * - 布局组件（divider/title）返回特殊组件
 * - custom 类型从 componentRegistry 取
 * - 其余返回对应的 Element Plus 组件
 */
function resolveComponent(field: FormFieldConfig): Component {
  switch (field.type) {
    case FieldType.INPUT:
    case FieldType.TEXTAREA:
    case FieldType.PASSWORD:
      return markRaw(ElInput)
    case FieldType.NUMBER:
      return markRaw(ElInputNumber)
    case FieldType.SELECT:
    case FieldType.RADIO:
    case FieldType.CHECKBOX:
      // select/radio/checkbox 在模板中分别渲染，这里返回占位
      return markRaw(ElInput)
    case FieldType.DATE:
    case FieldType.DATETIME:
    case FieldType.DATERANGE:
      return markRaw(ElDatePicker)
    case FieldType.SWITCH:
      return markRaw(ElSwitch)
    case FieldType.RATE:
      return markRaw(ElRate)
    case FieldType.SLIDER:
      return markRaw(ElSlider)
    case FieldType.CASCADER:
      return markRaw(ElCascader)
    case FieldType.UPLOAD:
      return markRaw(ElUpload)
    case FieldType.CUSTOM: {
      const name = (field.props?.componentName as string) || ''
      const comp = props.componentRegistry[name]
      if (!comp) {
        console.warn(`[LowCodeFormRenderer] 未注册的自定义组件: ${name}`)
        return markRaw(ElInput)
      }
      return markRaw(comp)
    }
    default:
      return markRaw(ElInput)
  }
}

/** 判断是否为 textarea 类型 */
function isTextarea(field: FormFieldConfig): boolean {
  return field.type === FieldType.TEXTAREA
}

/** 判断是否为 select 类型 */
function isSelect(field: FormFieldConfig): boolean {
  return field.type === FieldType.SELECT
}

/** 判断是否为 radio 类型 */
function isRadio(field: FormFieldConfig): boolean {
  return field.type === FieldType.RADIO
}

/** 判断是否为 checkbox 类型 */
function isCheckbox(field: FormFieldConfig): boolean {
  return field.type === FieldType.CHECKBOX
}

/** 判断是否为布局组件（divider/title，不渲染 el-form-item） */
function isLayoutField(field: FormFieldConfig): boolean {
  return field.type === FieldType.DIVIDER || field.type === FieldType.TITLE
}

/** 获取日期选择器的 type 属性 */
function dateType(field: FormFieldConfig): 'date' | 'datetime' | 'daterange' {
  if (field.type === FieldType.DATETIME) return 'datetime'
  if (field.type === FieldType.DATERANGE) return 'daterange'
  return 'date'
}

/** 字段 change 事件处理 */
function handleFieldChange(field: FormFieldConfig, value: unknown) {
  emit('field-change', field, value)
  const handlerName = field.events?.change
  if (handlerName && props.eventHandlers[handlerName]) {
    props.eventHandlers[handlerName](value, field, formData)
  }
}

/** 表单校验 */
async function validate(): Promise<boolean> {
  if (!formRef.value) return false
  try {
    await formRef.value.validate()
    return true
  } catch (errors) {
    emit('validate-fail', errors)
    return false
  }
}

/** 提交表单：先校验，通过后 emit submit */
async function submit(): Promise<void> {
  const ok = await validate()
  if (ok) {
    emit('submit', { ...formData })
  }
}

/** 重置表单到初始值 */
function resetFields(): void {
  formRef.value?.resetFields()
  // 同时重置默认值
  for (const field of props.config.fields || []) {
    if (field.defaultValue !== undefined) {
      formData[field.prop] = field.defaultValue
    } else {
      formData[field.prop] = field.type === FieldType.CHECKBOX ? [] : ''
    }
  }
}

/** 清除校验状态 */
function clearValidate(): void {
  formRef.value?.clearValidate()
}

// 暴露方法供父组件通过 ref 调用
defineExpose({
  validate,
  submit,
  resetFields,
  clearValidate,
  getFormData: () => ({ ...formData }),
  formRef
})
</script>

<template>
  <el-form
    ref="formRef"
    :model="formData"
    :rules="formRules"
    :label-width="config.labelWidth ?? 100"
    :label-position="config.labelPosition ?? 'right'"
    :size="config.size ?? 'default'"
    :disabled="disabled"
    class="low-code-form-renderer"
  >
    <!-- ============ Grid 布局（默认） ============ -->
    <el-row v-if="!layout.type || layout.type === 'grid'" :gutter="layout.gutter ?? 16">
      <el-col
        v-for="field in visibleFields"
        :key="field.id"
        v-bind="colProps(field.span)"
      >
        <!-- 分隔线 -->
        <el-divider
          v-if="field.type === 'divider'"
          :content-position="(field.props?.contentPosition as 'left' | 'center' | 'right') || 'center'"
          :border-style="(field.props?.borderStyle as string) || 'solid'"
        >
          {{ field.label }}
        </el-divider>
        <!-- 标题 -->
        <h3 v-else-if="field.type === 'title'" class="form-title" :style="{ fontSize: '16px' }">
          {{ field.label }}
        </h3>
        <!-- 普通表单项 -->
        <el-form-item
          v-else
          :label="field.label"
          :prop="field.prop"
        >
          <!-- 多行文本 -->
          <el-input
            v-if="isTextarea(field)"
            v-model="formData[field.prop]"
            type="textarea"
            :placeholder="field.placeholder"
            :disabled="field.disabled"
            :readonly="field.readonly"
            :clearable="field.clearable"
            :rows="(field.props?.rows as number) ?? 3"
            v-bind="field.props || {}"
            @change="(val: string) => handleFieldChange(field, val)"
          />
          <!-- 下拉选择 -->
          <el-select
            v-else-if="isSelect(field)"
            v-model="formData[field.prop]"
            :placeholder="field.placeholder"
            :disabled="field.disabled"
            :clearable="field.clearable"
            :multiple="(field.props?.multiple as boolean) ?? false"
            :filterable="(field.props?.filterable as boolean) ?? false"
            v-bind="field.props || {}"
            @change="(val: unknown) => handleFieldChange(field, val)"
          >
            <el-option
              v-for="opt in (field.props?.options as Array<{ label: string; value: unknown }>) || []"
              :key="String(opt.value)"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
          <!-- 单选组 -->
          <el-radio-group
            v-else-if="isRadio(field)"
            v-model="formData[field.prop]"
            :disabled="field.disabled"
            @change="(val: unknown) => handleFieldChange(field, val)"
          >
            <el-radio
              v-for="opt in (field.props?.options as Array<{ label: string; value: unknown }>) || []"
              :key="String(opt.value)"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-radio>
          </el-radio-group>
          <!-- 多选组 -->
          <el-checkbox-group
            v-else-if="isCheckbox(field)"
            v-model="formData[field.prop]"
            :disabled="field.disabled"
            @change="(val: unknown) => handleFieldChange(field, val)"
          >
            <el-checkbox
              v-for="opt in (field.props?.options as Array<{ label: string; value: unknown }>) || []"
              :key="String(opt.value)"
              :value="opt.value"
            >
              {{ opt.label }}
            </el-checkbox>
          </el-checkbox-group>
          <!-- 日期/日期时间/日期范围 -->
          <el-date-picker
            v-else-if="field.type === 'date' || field.type === 'datetime' || field.type === 'daterange'"
            v-model="formData[field.prop]"
            :type="dateType(field)"
            :placeholder="field.placeholder"
            :disabled="field.disabled"
            :readonly="field.readonly"
            :clearable="field.clearable"
            :format="(field.props?.format as string) || undefined"
            :value-format="(field.props?.valueFormat as string) || undefined"
            v-bind="field.props || {}"
            @change="(val: unknown) => handleFieldChange(field, val)"
          />
          <!-- 文件上传：不在 v-model 链，单独处理 -->
          <el-upload
            v-else-if="field.type === 'upload'"
            :action="(field.props?.action as string) || '/api/file/upload'"
            :limit="(field.props?.limit as number) || 5"
            :accept="(field.props?.accept as string) || ''"
            :multiple="(field.props?.multiple as boolean) ?? false"
            :list-type="(field.props?.listType as 'text' | 'picture' | 'picture-card') || 'text'"
            :disabled="field.disabled"
          >
            <el-button type="primary" :disabled="field.disabled">点击上传</el-button>
            <template v-if="field.props?.tip" #tip>
              <div class="el-upload__tip">{{ field.props.tip }}</div>
            </template>
          </el-upload>
          <!-- 自定义组件 -->
          <component
            :is="resolveComponent(field)"
            v-else-if="field.type === 'custom'"
            v-model="formData[field.prop]"
            :field="field"
            :disabled="field.disabled"
            v-bind="field.props || {}"
            @change="(val: unknown) => handleFieldChange(field, val)"
          />
          <!-- 默认：单行文本/数字/密码/开关/评分/滑块/级联 -->
          <component
            :is="resolveComponent(field)"
            v-else
            v-model="formData[field.prop]"
            :placeholder="field.placeholder"
            :disabled="field.disabled"
            :readonly="field.readonly"
            :clearable="field.clearable"
            v-bind="field.props || {}"
            @change="(val: unknown) => handleFieldChange(field, val)"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <!-- ============ Tabs 布局 ============ -->
    <el-tabs v-else-if="layout.type === 'tabs'" v-model="activeTab">
      <el-tab-pane
        v-for="(tab, idx) in layout.tabs || []"
        :key="idx"
        :label="tab.title"
        :name="tab.name || tab.title"
      >
        <el-row :gutter="layout.gutter ?? 16">
          <el-col
            v-for="field in resolveFields(tab.fields)"
            :key="field.id"
            v-bind="colProps(field.span)"
          >
            <el-form-item :label="field.label" :prop="field.prop">
              <component
                :is="resolveComponent(field)"
                v-model="formData[field.prop]"
                :placeholder="field.placeholder"
                :disabled="field.disabled"
                :readonly="field.readonly"
                :clearable="field.clearable"
                v-bind="field.props || {}"
                @change="(val: unknown) => handleFieldChange(field, val)"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-tab-pane>
    </el-tabs>

    <!-- ============ Collapse 布局 ============ -->
    <el-collapse v-else-if="layout.type === 'collapse'" v-model="activeCollapse">
      <el-collapse-item
        v-for="(group, idx) in layout.collapse || []"
        :key="idx"
        :title="group.title"
        :name="group.name || String(idx)"
      >
        <el-row :gutter="layout.gutter ?? 16">
          <el-col
            v-for="field in resolveFields(group.fields)"
            :key="field.id"
            v-bind="colProps(field.span)"
          >
            <el-form-item :label="field.label" :prop="field.prop">
              <component
                :is="resolveComponent(field)"
                v-model="formData[field.prop]"
                :placeholder="field.placeholder"
                :disabled="field.disabled"
                :readonly="field.readonly"
                :clearable="field.clearable"
                v-bind="field.props || {}"
                @change="(val: unknown) => handleFieldChange(field, val)"
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-collapse-item>
    </el-collapse>
  </el-form>
</template>

<style scoped>
.low-code-form-renderer {
  width: 100%;
}

.form-title {
  margin: 8px 0;
  padding-left: 8px;
  border-left: 4px solid var(--el-color-primary);
  color: var(--el-text-color-primary);
}
</style>
