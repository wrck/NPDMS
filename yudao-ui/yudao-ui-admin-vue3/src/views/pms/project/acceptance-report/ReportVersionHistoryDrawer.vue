<template>
  <el-drawer v-model="visible" title="报告版本历史" :size="narrow ? '100%' : '760px'">
    <el-skeleton v-if="loading" :rows="5" animated />
    <el-empty v-else-if="!versions.length" description="尚无报告版本" />
    <el-timeline v-else>
      <el-timeline-item v-for="item in versions" :key="item.id" :timestamp="item.effectiveFrom || '草稿未生效'" placement="top">
        <article class="version-card">
          <div class="version-heading">
            <div><strong>V{{ item.reportVersionNo }}</strong><span>{{ statusLabel(item.reportStatus) }}</span></div>
            <el-tag :type="archiveTag(item.archiveStatus)">{{ archiveStatusLabel(item.archiveStatus) }}</el-tag>
          </div>
          <dl class="version-facts">
            <div><dt>验收时间</dt><dd>{{ item.acceptanceTime || '未填写' }}</dd></div>
            <div><dt>验收人</dt><dd>{{ item.acceptorName || '未填写' }}</dd></div>
            <div><dt>结论</dt><dd>{{ item.conclusionCode || '未填写' }}</dd></div>
          </dl>
          <el-alert v-if="item.archiveStatus === 'PENDING_COMPENSATION'" title="归档待补偿，不影响当前报告及历史下载" type="warning" :closable="false" show-icon />
          <div v-if="item.attachments.length" class="attachment-list">
            <el-button v-for="attachment in item.attachments" :key="attachment.sequence" link type="primary" @click="download(item, attachment)">
              下载附件 {{ attachment.sequence }}
            </el-button>
          </div>
          <el-empty v-else description="该版本尚无冻结附件" :image-size="44" />
        </article>
      </el-timeline-item>
    </el-timeline>
  </el-drawer>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import * as ReportApi from '@/api/pms/project/acceptance-report'
import * as FileApi from '@/api/pms/platform/file'
import type { AcceptanceReportVersionVO, ReportAttachmentVO } from '@/api/pms/project/acceptance-report'

const message = useMessage()
const narrow = useMediaQuery('(width <= 767px)')
const visible = ref(false)
const loading = ref(false)
const acceptanceId = ref<number>()
const versions = ref<AcceptanceReportVersionVO[]>([])

const open = async (id: number) => {
  acceptanceId.value = id
  visible.value = true
  loading.value = true
  try { versions.value = await ReportApi.getReportVersions(id) } finally { loading.value = false }
}

const download = async (report: AcceptanceReportVersionVO, attachment: ReportAttachmentVO) => {
  if (!acceptanceId.value) return
  const target = window.open('about:blank', '_blank')
  if (!target) return message.warning('浏览器已阻止新窗口，请允许弹窗后重试')
  target.opener = null
  try {
    const fact = await ReportApi.downloadAttachment(acceptanceId.value, report.id, attachment.sequence)
    const ticket = await FileApi.createAccessTicket(fact.artifactId, fact.versionNo, 'DOWNLOAD', {
      ownerContext: 'ACC', objectType: 'ACCEPTANCE_REPORT_VERSION', objectId: String(report.id),
      purposeCode: 'ACCEPTANCE_REPORT_ATTACHMENT', referenceKey: fact.referenceKey
    })
    target.location.replace(ticket.shortLivedUrl)
  } catch (error) { target.close(); throw error }
}

const statusLabel = (status: string) => ({ DRAFT: '草稿', EFFECTIVE: '当前有效', SUPERSEDED: '已替换', REVOKED: '已撤销' })[status] || status
const archiveStatusLabel = (status?: string) => ({ ARCHIVED: '已归档', PENDING_COMPENSATION: '待补偿', REVOKED: '已撤销来源' })[status || ''] || (status ? status : '未进入归档')
const archiveTag = (status?: string) => (status === 'ARCHIVED' ? 'success' : status === 'PENDING_COMPENSATION' ? 'warning' : 'info')
defineExpose({ open })
</script>

<style scoped lang="scss">
.version-card { padding: 16px; border: 1px solid var(--el-border-color-lighter); border-radius: var(--el-border-radius-base); background: var(--el-fill-color-blank); }
.version-heading, .version-heading > div, .attachment-list { display: flex; align-items: center; gap: 10px; }
.version-heading { justify-content: space-between; }
.version-facts { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 16px 0; }
.version-facts div { min-width: 0; }
.version-facts dt { font-size: 12px; color: var(--el-text-color-secondary); }
.version-facts dd { margin: 4px 0 0; overflow-wrap: anywhere; color: var(--el-text-color-primary); }
.attachment-list { flex-wrap: wrap; margin-top: 12px; }
@media (width <= 767px) { .version-facts { grid-template-columns: 1fr; } .version-heading { align-items: flex-start; flex-direction: column; } }
</style>
