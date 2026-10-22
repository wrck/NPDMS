<template>
  <div class="record-actions">
    <el-button link type="primary" :disabled="busy" @click="emit('view', record)">查看</el-button>
    <el-button v-if="!readonly && record.status === 0" v-hasPermi="['pms:acc-completion-certificate:update']" link type="primary" :disabled="busy" @click="emit('edit', record)">编辑</el-button>
    <el-button v-if="!readonly && (record.status === 0 || record.status === 1)" v-hasPermi="['pms:acc-completion-certificate:submit']" type="primary" size="small" :loading="busy" @click="emit('action', { row: record, action: 'issueConfirmation', actionText: record.status === 1 ? '重新推送' : '发起调查' })">{{ record.status === 1 ? '重新推送' : '发起调查' }}</el-button>
    <el-dropdown trigger="click" :disabled="busy">
      <el-button link :disabled="busy" aria-label="更多操作">更多<Icon icon="ep:arrow-down" /></el-button>
      <template #dropdown><el-dropdown-menu>
        <el-dropdown-item :disabled="record.status === 4" @click="emit('print', { row: record, kind: 'COMPLETION' })">下载完工证明 PDF</el-dropdown-item>
        <el-dropdown-item :disabled="record.status === 0 || record.status === 4" @click="emit('print', { row: record, kind: 'SATISFACTION' })">下载满意度 PDF</el-dropdown-item>
        <el-dropdown-item v-if="!readonly && record.status === 1" v-hasPermi="['pms:acc-completion-certificate:audit']" divided @click="emit('action', { row: record, action: 'rejectCompletionCertificate', actionText: '驳回' })">驳回</el-dropdown-item>
        <el-dropdown-item v-if="!readonly && record.status === 2" v-hasPermi="['pms:acc-completion-certificate:audit']" divided @click="emit('action', { row: record, action: 'archiveCompletionCertificate', actionText: '归档' })">归档</el-dropdown-item>
        <el-dropdown-item v-if="!readonly && record.status === 0" v-hasPermi="['pms:acc-completion-certificate:delete']" divided @click="emit('remove', record)">删除草稿</el-dropdown-item>
      </el-dropdown-menu></template>
    </el-dropdown>
  </div>
</template>
<script lang="ts">
import type { CompletionCertificateVO } from '@/api/pms/acceptance/completion-certificate'
import type { ConfirmationDocumentKind } from './confirmationPrintForm'
export interface ConfirmationRecordAction {
  row: CompletionCertificateVO
  action: 'issueConfirmation' | 'rejectCompletionCertificate' | 'archiveCompletionCertificate'
  actionText: string
}
export interface ConfirmationRecordPrint { row: CompletionCertificateVO; kind: ConfirmationDocumentKind }
</script>
<script setup lang="ts">
defineProps<{ record: CompletionCertificateVO; readonly?: boolean; busy?: boolean }>()
const emit = defineEmits<{ view: [record: CompletionCertificateVO]; edit: [record: CompletionCertificateVO]; action: [command: ConfirmationRecordAction]; print: [selection: ConfirmationRecordPrint]; remove: [record: CompletionCertificateVO] }>()
</script>
<style scoped>
.record-actions { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.record-actions .el-button + .el-button { margin-left: 0; }
@media (width <= 900px) { .record-actions :deep(.el-button) { min-height: 44px; } }
</style>
