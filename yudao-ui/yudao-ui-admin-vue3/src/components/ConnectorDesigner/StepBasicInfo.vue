<!-- src/components/ConnectorDesigner/StepBasicInfo.vue -->
<script setup lang="ts">
/**
 * 连接器分步表单 — Step 1 基本信息。
 *
 * <p>借鉴 Power Apps Custom Connectors 的第一步：编码 / 名称 / 描述 / 类型。</p>
 */
import { reactive, watch } from 'vue'
import type { ConnectorType } from '@/api/lowcode-connector'

const props = defineProps<{
  code: string
  name: string
  description: string
  bizType: string
  type: ConnectorType
}>()

const emit = defineEmits<{
  'update:code': [value: string]
  'update:name': [value: string]
  'update:description': [value: string]
  'update:bizType': [value: string]
  'update:type': [value: ConnectorType]
}>()

const form = reactive({
  code: props.code,
  name: props.name,
  description: props.description,
  bizType: props.bizType,
  type: props.type
})

watch(
  () => [props.code, props.name, props.description, props.bizType, props.type],
  ([code, name, description, bizType, type]) => {
    form.code = code as string
    form.name = name as string
    form.description = description as string
    form.bizType = bizType as string
    form.type = type as ConnectorType
  }
)

watch(form, (val) => {
  emit('update:code', val.code)
  emit('update:name', val.name)
  emit('update:description', val.description)
  emit('update:bizType', val.bizType)
  emit('update:type', val.type)
})
</script>

<template>
  <el-form :model="form" label-width="100px" class="step-basic-info">
    <el-form-item label="连接器编码" required>
      <el-input v-model="form.code" placeholder="如 githubConnector" />
    </el-form-item>
    <el-form-item label="连接器名称" required>
      <el-input v-model="form.name" placeholder="如 GitHub 连接器" />
    </el-form-item>
    <el-form-item label="描述">
      <el-input
        v-model="form.description"
        type="textarea"
        :rows="3"
        placeholder="连接器用途说明"
      />
    </el-form-item>
    <el-form-item label="业务类型">
      <el-input v-model="form.bizType" placeholder="如 integration / external-api" />
    </el-form-item>
    <el-form-item label="连接器类型" required>
      <el-radio-group v-model="form.type">
        <el-radio-button value="REST">REST API</el-radio-button>
        <el-radio-button value="DB">数据库 (DB)</el-radio-button>
      </el-radio-group>
      <div class="type-hint">
        <span v-if="form.type === 'REST'">REST 类型：通过 HTTP 调用外部 API，支持认证、操作、分页</span>
        <span v-else>DB 类型：直连数据库执行 SQL，支持数据源池化与 SQL 模板</span>
      </div>
    </el-form-item>
  </el-form>
</template>

<style scoped lang="scss">
.step-basic-info {
  max-width: 640px;
}
.type-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}
</style>
