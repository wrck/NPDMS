<template>
  <el-drawer v-model="visible" :size="narrow ? '100%' : '640px'" title="逐项豁免">
    <el-alert
      title="豁免仅替代所选阻断，不改变工勘事实。过期或撤回后将重新阻断。"
      type="warning"
      :closable="false"
    />
    <el-form label-position="top" class="waiver-form">
      <el-form-item label="阻断编码（逗号分隔）"
        ><el-input v-model="form.blockerCodes"
      /></el-form-item>
      <el-form-item label="申请原因"
        ><el-input v-model="form.reason" type="textarea" :rows="3"
      /></el-form-item>
      <el-form-item label="风险"
        ><el-input v-model="form.risk" type="textarea" :rows="2"
      /></el-form-item>
      <el-form-item label="补偿措施"
        ><el-input v-model="form.compensation" type="textarea" :rows="2"
      /></el-form-item>
      <div class="date-grid">
        <el-form-item label="生效时间"
          ><el-date-picker v-model="form.validFrom" type="datetime" value-format="x"
        /></el-form-item>
        <el-form-item label="失效时间"
          ><el-date-picker v-model="form.validUntil" type="datetime" value-format="x"
        /></el-form-item>
      </div>
      <el-button
        v-if="item?.allowedActions?.includes('CREATE_WAIVER')"
        type="primary"
        :loading="saving"
        @click="create"
        >申请豁免</el-button
      >
    </el-form>
    <el-divider>历史记录</el-divider>
    <el-skeleton v-if="loading" :rows="4" animated />
    <el-empty v-else-if="!rows.length" description="暂无豁免记录" />
    <div v-else class="waiver-list">
      <article v-for="row in rows" :key="row.waiverId" class="waiver-row">
        <div class="waiver-heading"
          ><strong>#{{ row.waiverNo }} · {{ row.statusCode }}</strong
          ><span>V{{ row.version }}</span></div
        >
        <p>{{ row.reason || '无申请说明' }}</p>
        <small>{{ formatDateTime(row.validFrom) }} 至 {{ formatDateTime(row.validUntil) }}</small>
        <div class="row-actions">
          <el-button
            v-if="row.allowedActions?.includes('SUBMIT')"
            link
            type="primary"
            @click="act(row, 'submit')"
            >提交</el-button
          >
          <el-button
            v-if="row.allowedActions?.includes('APPROVE')"
            link
            type="success"
            @click="act(row, 'approve')"
            >批准</el-button
          >
          <el-button
            v-if="row.allowedActions?.includes('REJECT')"
            link
            type="danger"
            @click="act(row, 'reject')"
            >驳回</el-button
          >
          <el-button
            v-if="row.allowedActions?.includes('WITHDRAW')"
            link
            type="warning"
            @click="act(row, 'withdraw')"
            >撤回</el-button
          >
        </div>
      </article>
    </div>
    <el-button v-if="hasMore" class="load-more" @click="load(false)">加载更多</el-button>
  </el-drawer>
</template>

<script setup lang="ts">
import { useMediaQuery } from '@vueuse/core'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate } from '@/utils/formatTime'
import * as PreparationApi from '@/api/pms/engineering/preparation'
import type { PreparationItemVO, PreparationVO, WaiverVO } from '@/api/pms/engineering/preparation'
import { createIntentKeyStore, intentOf } from './preparationInteraction'

const props = defineProps<{ projectVersion: number }>()
const emit = defineEmits<{ changed: [] }>()
const message = useMessage()
const narrow = useMediaQuery('(max-width: 767px)')
const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const preparation = ref<PreparationVO>()
const item = ref<PreparationItemVO>()
const rows = ref<WaiverVO[]>([])
const cursor = ref<string>()
const hasMore = ref(false)
const form = reactive({
  blockerCodes: '',
  reason: '',
  risk: '',
  compensation: '',
  validFrom: '',
  validUntil: ''
})
const intentKeys = createIntentKeyStore()
const formatDateTime = (value?: string | number) => (value ? formatDate(value) : '-')

const open = async (current: PreparationVO, row: PreparationItemVO) => {
  preparation.value = current
  item.value = row
  visible.value = true
  await load(true)
}
const load = async (reset: boolean) => {
  if (!preparation.value || !item.value) return
  loading.value = true
  try {
    const page = await PreparationApi.getWaivers(
      preparation.value.preparationId,
      item.value.itemId,
      {
        cursor: reset ? undefined : cursor.value,
        pageSize: 20
      }
    )
    rows.value = reset ? page.items : [...rows.value, ...page.items]
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } finally {
    loading.value = false
  }
}
const create = async () => {
  if (!preparation.value || !item.value) return
  saving.value = true
  try {
    const payload = {
      blockerCodes: form.blockerCodes
        .split(',')
        .map((value) => value.trim())
        .filter(Boolean),
      reason: form.reason,
      risk: form.risk,
      compensation: form.compensation,
      validFrom: form.validFrom,
      validUntil: form.validUntil
    }
    const intent = intentOf('CREATE_WAIVER', {
      preparationId: preparation.value.preparationId,
      preparationVersion: preparation.value.version,
      itemId: item.value.itemId,
      itemVersion: item.value.version,
      projectVersion: props.projectVersion,
      ...payload
    })
    await PreparationApi.createWaiver(
      preparation.value,
      item.value,
      props.projectVersion,
      payload,
      intentKeys.key(intent)
    )
    intentKeys.complete(intent)
    message.success('豁免申请草稿已创建')
    visible.value = false
    emit('changed')
  } finally {
    saving.value = false
  }
}
const act = async (row: WaiverVO, action: 'submit' | 'approve' | 'reject' | 'withdraw') => {
  if (!preparation.value || !item.value) return
  let opinion: string | undefined
  if (action === 'approve' || action === 'reject')
    opinion = (await message.prompt('请输入审批意见', '豁免审批')).value
  const intent = intentOf(`WAIVER_${action}`, {
    preparationId: preparation.value.preparationId,
    preparationVersion: preparation.value.version,
    itemId: item.value.itemId,
    itemVersion: item.value.version,
    waiverId: row.waiverId,
    waiverVersion: row.version,
    projectVersion: props.projectVersion,
    opinion
  })
  await PreparationApi.actWaiver(
    preparation.value,
    item.value,
    row,
    action,
    props.projectVersion,
    opinion,
    intentKeys.key(intent)
  )
  intentKeys.complete(intent)
  message.success('豁免状态已更新')
  visible.value = false
  emit('changed')
}

defineExpose({ open })
</script>

<style scoped lang="scss">
.waiver-form {
  margin-top: 16px;
}

.date-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.waiver-list {
  display: grid;
  gap: 10px;
}

.waiver-row {
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: var(--el-border-radius-base);
}

.waiver-heading {
  display: flex;
  justify-content: space-between;
  color: var(--el-text-color-primary);
}

.waiver-row p,
.waiver-row small {
  color: var(--el-text-color-secondary);
}

.row-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.load-more {
  width: 100%;
  margin-top: 12px;
}

@media (width <= 767px) {
  .date-grid {
    grid-template-columns: 1fr;
  }
}
</style>
