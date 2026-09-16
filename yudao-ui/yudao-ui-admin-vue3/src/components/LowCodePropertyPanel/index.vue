<template>
  <div class="lowcode-property-panel">
    <div class="panel-header">{{ meta?.displayName || '属性配置' }}</div>
    <el-form label-width="110px" size="small">
      <PropField
        v-for="prop in meta?.propsSchema || []"
        :key="prop.key"
        :prop-def="prop"
        :model-value="local[prop.key]"
        @update:model-value="(v: any) => (local[prop.key] = v)"
      />
    </el-form>
  </div>
</template>

<script setup lang="ts">
/**
 * 低代码属性面板（schema 驱动，借鉴 NocoBase JSONSchema）。
 *
 * <p>根据传入的 {@link ComponentMeta.propsSchema} 动态渲染任意属性表单，
 * 每个属性委托给递归组件 {@link PropField} 渲染对应控件。
 * 支持 boolean/number/string/select/color/date/array/object/code/expression 类型，
 * 是后续 4 个设计器打通组件注册中心的基础。</p>
 *
 * <p>通过 v-model 双向绑定到 props.modelValue；内部维护 local 响应式副本，
 * 深度监听后回传父组件。挂载时按 propDef.default 初始化缺失字段。</p>
 */
import { reactive, watch } from 'vue'
import type { ComponentMeta } from '@/components/LowCodeComponentRegistry/types'
import PropField from './PropField.vue'

const props = defineProps<{ meta: ComponentMeta | null; modelValue: Record<string, any> }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: Record<string, any>): void }>()

// 双向绑定（modelValue 是对象，需拷贝后修改）
const local = reactive<Record<string, any>>({ ...props.modelValue })

// 挂载/ schema 变化时，按 default 初始化缺失字段
function initDefaults() {
  const schema = props.meta?.propsSchema || []
  for (const def of schema) {
    if (local[def.key] === undefined && def.default !== undefined) {
      local[def.key] = def.default
    }
  }
}
initDefaults()

watch(local, () => emit('update:modelValue', { ...local }), { deep: true })

// 父组件 modelValue 外部变更时同步到 local（避免循环：仅同步 local 缺失或不一致的键）
watch(
  () => props.modelValue,
  (val) => {
    if (!val) return
    for (const key of Object.keys(val)) {
      if (local[key] !== val[key]) {
        local[key] = val[key]
      }
    }
  },
  { deep: true }
)
</script>

<style scoped>
.lowcode-property-panel {
  padding: 8px;
}
.panel-header {
  font-weight: 600;
  padding: 8px 0;
  border-bottom: 1px solid #ebeef5;
  margin-bottom: 8px;
}
</style>
