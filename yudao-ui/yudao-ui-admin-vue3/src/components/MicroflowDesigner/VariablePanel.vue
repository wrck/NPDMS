<script setup lang="ts">
/**
 * 微流变量面板（右侧 Tab，借鉴 Mendix Microflows Parameters）。
 *
 * <p>管理三类变量：</p>
 * <ul>
 *   <li>输入参数（inputs）：调用方传入</li>
 *   <li>局部变量（locals）：微流内部声明</li>
 *   <li>返回值类型（returnType）：微流出口类型</li>
 * </ul>
 *
 * <p>通过 v-model:variables 双向绑定；变量类型可选 STRING/INTEGER/LONG/DECIMAL/
 * BOOLEAN/DATE/DATETIME/OBJECT/LIST/Map 等。</p>
 */
import { computed } from 'vue'
import type { MicroflowVariable, MicroflowVariables } from '@/api/lowcode-microflow'

defineOptions({ name: 'MicroflowVariablePanel' })

const props = defineProps<{
  variables: MicroflowVariables
}>()

const emit = defineEmits<{
  (e: 'update:variables', v: MicroflowVariables): void
}>()

const VARIABLE_TYPES = [
  'String', 'Integer', 'Long', 'Double', 'Boolean',
  'Date', 'DateTime', 'Object', 'List', 'Map', 'BigDecimal'
]

const localInputs = computed<MicroflowVariable[]>({
  get: () => props.variables.inputs || [],
  set: (val) => emit('update:variables', { ...props.variables, inputs: val })
})

const localLocals = computed<MicroflowVariable[]>({
  get: () => props.variables.locals || [],
  set: (val) => emit('update:variables', { ...props.variables, locals: val })
})

const localReturnType = computed<string>({
  get: () => props.variables.returnType || 'Object',
  set: (val) => emit('update:variables', { ...props.variables, returnType: val })
})

function addInput() {
  localInputs.value = [...localInputs.value, { name: `input${localInputs.value.length + 1}`, type: 'String' }]
}

function removeInput(idx: number) {
  const arr = [...localInputs.value]
  arr.splice(idx, 1)
  localInputs.value = arr
}

function addLocal() {
  localLocals.value = [...localLocals.value, { name: `var${localLocals.value.length + 1}`, type: 'Object' }]
}

function removeLocal(idx: number) {
  const arr = [...localLocals.value]
  arr.splice(idx, 1)
  localLocals.value = arr
}
</script>

<template>
  <div class="variable-panel">
    <div class="section">
      <div class="section-header">
        <span>输入参数</span>
        <el-button size="small" type="primary" link @click="addInput">+ 添加</el-button>
      </div>
      <div v-for="(item, idx) in localInputs" :key="`in-${idx}`" class="var-row">
        <el-input v-model="item.name" size="small" placeholder="参数名" />
        <el-select v-model="item.type" size="small" placeholder="类型">
          <el-option v-for="t in VARIABLE_TYPES" :key="t" :label="t" :value="t" />
        </el-select>
        <el-button size="small" type="danger" link @click="removeInput(idx)">删除</el-button>
      </div>
      <div v-if="localInputs.length === 0" class="empty-tip">暂无输入参数</div>
    </div>

    <div class="section">
      <div class="section-header">
        <span>局部变量</span>
        <el-button size="small" type="primary" link @click="addLocal">+ 添加</el-button>
      </div>
      <div v-for="(item, idx) in localLocals" :key="`loc-${idx}`" class="var-row">
        <el-input v-model="item.name" size="small" placeholder="变量名" />
        <el-select v-model="item.type" size="small" placeholder="类型">
          <el-option v-for="t in VARIABLE_TYPES" :key="t" :label="t" :value="t" />
        </el-select>
        <el-button size="small" type="danger" link @click="removeLocal(idx)">删除</el-button>
      </div>
      <div v-if="localLocals.length === 0" class="empty-tip">暂无局部变量</div>
    </div>

    <div class="section">
      <div class="section-header"><span>返回值类型</span></div>
      <el-select v-model="localReturnType" size="small" placeholder="返回类型" style="width: 100%">
        <el-option v-for="t in VARIABLE_TYPES" :key="t" :label="t" :value="t" />
      </el-select>
    </div>
  </div>
</template>

<style scoped lang="scss">
.variable-panel {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafafa;
  padding: 8px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 6px;
  color: #303133;
}

.var-row {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 4px;
  .el-input {
    flex: 1;
  }
  .el-select {
    width: 100px;
  }
}

.empty-tip {
  font-size: 12px;
  color: #c0c4cc;
  text-align: center;
  padding: 4px 0;
}
</style>
