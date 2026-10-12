<template>
  <section v-if="result" aria-label="设备日志结构化结果">
    <div class="flex items-center justify-between gap-12px my-12px flex-wrap">
      <span
        >解析版本 {{ result.coordinate.releaseVersion }} ·
        {{ semantic.observations.length }} 个日志块</span
      >
      <el-button @click="exportJson">导出结构化 JSON</el-button>
    </div>
    <el-alert
      v-for="warning in semantic.profileSelection?.warnings || []"
      :key="warning"
      :title="warning"
      type="warning"
      :closable="false"
      class="mb-8px"
    />
    <el-tabs>
      <el-tab-pane v-if="semantic.genericContent" label="结构化数据">
        <SemanticGenericStructureView
          :content="semantic.genericContent"
          :observations="semantic.observations"
          :nested-observations="semantic.nestedObservations"
        />
      </el-tab-pane>
      <el-tab-pane label="设备实体" lazy>
        <SemanticEntityView :projections="semantic.projections" />
      </el-tab-pane>
      <el-tab-pane label="字段与来源" lazy>
        <SemanticEvidenceTable :rows="flattenSemanticEvidence(semantic.snapshot)" />
      </el-tab-pane>
      <el-tab-pane label="识别情况" lazy>
        <el-table :data="semantic.observations" size="small">
          <el-table-column prop="commandIndex" label="日志块" width="90" />
          <el-table-column prop="blockRole" label="识别类型" min-width="150" />
          <el-table-column label="结果" min-width="140">
            <template #default="{ row }">{{
              observationStates[row.status] || row.status
            }}</template>
          </el-table-column>
          <el-table-column label="提示" min-width="170">
            <template #default="{ row }">{{ row.warnings.join('；') || '—' }}</template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="JSON" lazy>
        <pre class="max-h-600px overflow-auto whitespace-pre-wrap break-all text-xs">{{
          JSON.stringify(semantic, null, 2)
        }}</pre>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { ParseResultEnvelope } from './semantic/types'
import SemanticEntityView from './semantic/SemanticEntityView.vue'
import SemanticEvidenceTable from './semantic/SemanticEvidenceTable.vue'
import SemanticGenericStructureView from './semantic/SemanticGenericStructureView.vue'
import { flattenSemanticEvidence } from './semantic/evidence'

const props = defineProps<{ result: ParseResultEnvelope }>()
const semantic = computed(() => props.result.semanticResult)
const observationStates: Record<string, string> = {
  OBSERVED: '已识别',
  UNPARSED: '未识别，保留文本',
  NO_DATA: '无数据',
  NOT_ENABLED: '功能未启用',
  REDACTED: '已脱敏',
  PARTIAL: '部分识别',
  SOURCE_CORRUPTED: '日志损坏',
  EXECUTION_FAILED: '原命令未成功'
}
const exportJson = () => {
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(props.result, null, 2)], { type: 'application/json' })
  )
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `device-log-${props.result.taskId}.json`
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>
