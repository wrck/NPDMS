<script setup lang="ts">
/**
 * 微流元信息面板（右栏，未选中节点时显示，借鉴 Mendix Microflows 属性）。
 *
 * <p>展示并编辑微流基础信息：编码 / 名称 / 描述 / 状态。
 * 通过 v-model:microflow 与父组件双向绑定；下方由父组件挂载变量面板
 * （VariablePanel）管理输入参数与局部变量。</p>
 */
import { computed } from 'vue'
import type { LowCodeMicroflow } from '@/api/lowcode-microflow'

defineOptions({ name: 'MicroflowMetaPanel' })

const props = defineProps<{
  microflow: LowCodeMicroflow | null
}>()

const emit = defineEmits<{
  (e: 'update:microflow', v: LowCodeMicroflow): void
}>()

/** 各字段双向绑定代理：读取 props，写入时整体 emit 回父组件 */
const code = computed<string>({
  get: () => props.microflow?.code || '',
  set: (v) => props.microflow && emit('update:microflow', { ...props.microflow, code: v })
})

const name = computed<string>({
  get: () => props.microflow?.name || '',
  set: (v) => props.microflow && emit('update:microflow', { ...props.microflow, name: v })
})

const description = computed<string>({
  get: () => props.microflow?.description || '',
  set: (v) => props.microflow && emit('update:microflow', { ...props.microflow, description: v })
})

const status = computed(() => props.microflow?.status || 'DRAFT')
</script>

<template>
  <div class="microflow-meta-panel">
    <div class="panel-header">
      <el-icon><Document /></el-icon>
      <span>微流信息</span>
    </div>
    <el-form v-if="microflow" label-width="72px" size="small" class="meta-form">
      <el-form-item label="编码">
        <el-input v-model="code" placeholder="如 order_approval" />
      </el-form-item>
      <el-form-item label="名称">
        <el-input v-model="name" placeholder="微流显示名称" />
      </el-form-item>
      <el-form-item label="描述">
        <el-input v-model="description" type="textarea" :rows="2" placeholder="微流用途说明" />
      </el-form-item>
      <el-form-item label="状态">
        <el-tag :type="status === 'PUBLISHED' ? 'success' : 'info'">{{ status }}</el-tag>
      </el-form-item>
    </el-form>
    <div v-else class="empty-state">
      <el-icon><InfoFilled /></el-icon>
      <div>请选择或新建微流</div>
    </div>
  </div>
</template>

<style scoped lang="scss">
.microflow-meta-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.panel-header {
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  gap: 6px;
  background: #fafafa;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}

.meta-form {
  padding: 10px 12px;
  overflow-y: auto;
}

.empty-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  font-size: 13px;
  gap: 6px;

  .el-icon {
    font-size: 32px;
  }
}
</style>
