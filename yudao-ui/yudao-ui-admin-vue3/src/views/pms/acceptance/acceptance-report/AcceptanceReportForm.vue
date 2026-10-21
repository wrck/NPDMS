<template>
  <el-form label-position="top" class="report-form" data-testid="acceptance-report-form" :data-readonly="readonly">
    <el-row :gutter="16">
      <el-col :xs="24" :sm="12">
        <el-form-item label="验收人"><span class="auto-value">{{ operatorName || '—' }}</span></el-form-item>
      </el-col>
      <el-col :xs="24" :sm="12">
        <el-form-item label="完成时间"><span class="auto-value">{{ completionTimeText }}</span></el-form-item>
      </el-col>
      <el-col :span="24">
        <el-form-item label="上传交付件">
          <div class="report-files">
            <section v-for="report in reports" :key="report.id" class="file-group">
              <el-alert v-if="report.archiveStatus === 'PENDING_COMPENSATION'" title="报告已生效，交付件归档待补偿；历史下载保持可用" type="warning" show-icon :closable="false" />
              <div v-if="readonly || report.attachments.length" class="file-heading">
                <span>{{ report.reportStatus === 'DRAFT' ? report.attachments.length ? '已上传的草稿交付件' : '草稿交付件' : '当前有效报告' }} V{{ report.reportVersionNo }}</span>
                <el-tag :type="report.reportStatus === 'DRAFT' ? 'warning' : 'success'">{{ report.reportStatus === 'DRAFT' ? '尚未生效' : '已生效' }}</el-tag>
              </div>
              <div v-for="attachment in report.attachments" :key="attachment.referenceKey" class="report-file">
                <PmsFileVersionReferenceList owner-context="ACC" object-type="ACCEPTANCE_REPORT_VERSION" :object-id="String(report.id)"
                  purpose-code="ACCEPTANCE_REPORT_ATTACHMENT" :reference-key="attachment.referenceKey"
                  :artifact-id="attachment.artifactId" :version-no="attachment.versionNo" />
              </div>
            </section>
            <span v-if="readonly && !hasFiles" class="auto-value">未上传</span>
            <template v-if="!readonly">
              <p v-if="uploadedCount" class="upload-count">本次已上传 {{ uploadedCount }} 个</p>
              <slot name="uploader" />
            </template>
          </div>
        </el-form-item>
      </el-col>
    </el-row>
  </el-form>
</template>
<script setup lang="ts">
import { formatDate } from '@/utils/formatTime'
import type { AcceptanceReportVersionVO } from '@/api/pms/acceptance/acceptance-report'
import PmsFileVersionReferenceList from '@/components/PmsFileArtifact/PmsFileVersionReferenceList.vue'

type ReportFiles = Pick<AcceptanceReportVersionVO, 'id' | 'reportVersionNo' | 'reportStatus' | 'archiveStatus' | 'attachments'>
const props = defineProps<{
  operatorName?: string
  completionTime?: AcceptanceReportVersionVO['acceptanceTime'] | string
  reports: ReportFiles[]
  readonly?: boolean
  uploadedCount?: number
}>()
const completionTimeText = computed(() => props.completionTime == null || props.completionTime === ''
  ? props.readonly ? '—' : '保存后自动记录'
  : formatDate(new Date(props.completionTime)))
const hasFiles = computed(() => props.reports.some(report => report.attachments.length))
</script>
<style scoped>
.auto-value { color: var(--el-text-color-regular); line-height: 32px; }
.report-files { width: 100%; min-width: 0; }
.file-group + .file-group { margin-top: 16px; }
.file-heading { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; color: var(--el-text-color-secondary); }
.report-file + .report-file { margin-top: 12px; }
.upload-count { margin: 12px 0; color: var(--el-text-color-secondary); }
</style>
