<template>
  <el-card shadow="never" class="mt-8px">
    <template #header>
      <div class="flex items-center justify-between">
        <span>审批（统一审批能力）</span>
        <el-button link type="primary" :loading="loading" @click="reload">刷新</el-button>
      </div>
    </template>

    <el-alert v-if="panelError" :title="panelError" type="error" :closable="false" show-icon class="mb-8px" />

    <!-- 发起审批：公共契约字段，不按实体分支 -->
    <el-form inline class="mb-8px">
      <el-form-item label="审批目的">
        <el-input v-model="submitForm.purpose" placeholder="如 TICKET_CLOSE" style="width: 160px" />
      </el-form-item>
      <el-form-item label="提交依据">
        <el-input v-model="submitForm.submissionBasis" placeholder="如 单号/快照标识" style="width: 180px" />
      </el-form-item>
      <el-form-item label="流程引用">
        <el-input v-model="submitForm.neutralProcessRef" placeholder="如 proc/ticket-close" style="width: 180px" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="submit">
          发起审批
        </el-button>
      </el-form-item>
    </el-form>

    <h4>审批尝试</h4>
    <el-table
      ref="attemptTableRef"
      :data="attempts"
      row-key="id"
      size="small"
      border
      highlight-current-row
      @current-change="selectAttempt"
    >
      <el-table-column prop="id" label="#" width="60" />
      <el-table-column prop="attemptId" label="尝试键" min-width="140" show-overflow-tooltip />
      <el-table-column prop="purpose" label="目的" width="130" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="conclusionBasis" label="结论依据" min-width="120" show-overflow-tooltip />
      <el-table-column prop="instanceRef" label="流程实例" min-width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button v-if="row.status === 'PENDING'" link type="warning" size="small" @click="withdraw(row)">
            撤回
          </el-button>
          <el-button
            v-if="row.status !== 'PENDING' && row.status !== 'WITHDRAWN'"
            link
            type="primary"
            size="small"
            @click="resubmit(row)"
          >
            重提
          </el-button>
          <el-button v-if="row.status === 'APPROVED'" link type="success" size="small" @click="prepareEffect(row)">
            执行生效
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 决定办理：选中进行中的尝试后批准/驳回 -->
    <template v-if="selected">
      <h4 class="mt-12px">办理：尝试 #{{ selected.id }}（{{ selected.attemptId }}）</h4>
      <el-form inline class="mb-8px">
        <el-form-item label="审批意见">
          <el-input v-model="comment" placeholder="批准/驳回说明" style="width: 220px" />
        </el-form-item>
        <el-form-item>
          <el-button type="success" :loading="acting" :disabled="selected.status !== 'PENDING'" @click="decide(true)">
            批准
          </el-button>
          <el-button type="danger" :loading="acting" :disabled="selected.status !== 'PENDING'" @click="decide(false)">
            驳回
          </el-button>
        </el-form-item>
      </el-form>

      <h4 class="mt-12px">意见与生效台账</h4>
      <el-table :data="opinions" size="small" border class="mb-8px">
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column prop="action" label="动作" width="100" />
        <el-table-column prop="comment" label="意见" min-width="160" show-overflow-tooltip />
        <el-table-column prop="actorUserId" label="操作人" width="90" />
        <el-table-column prop="createTime" label="时间" min-width="160" />
      </el-table>
      <el-table :data="effects" size="small" border>
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column prop="operationCode" label="生效命令" width="140" />
        <el-table-column prop="idempotencyKey" label="幂等键" min-width="140" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'warning'" size="small">
              {{ effectStatusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="receiptOutcome" label="回执" width="120" />
        <el-table-column prop="detail" label="详情" min-width="160" show-overflow-tooltip />
      </el-table>

      <!-- 生效命令执行：仅批准后的尝试；命令必须是目录声明的操作 -->
      <template v-if="selected.status === 'APPROVED'">
        <h4 class="mt-12px">执行生效命令</h4>
        <el-form inline class="mb-8px">
          <el-form-item label="命令">
            <el-select v-model="effectForm.operationCode" placeholder="选择声明操作" style="width: 180px">
              <el-option
                v-for="operation in effectOperations"
                :key="operation.code"
                :label="`${operation.name}（${operation.code}）`"
                :value="operation.code"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="幂等键">
            <el-input v-model="effectForm.idempotencyKey" style="width: 220px" />
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              :loading="acting"
              :disabled="!effectForm.operationCode || !effectForm.idempotencyKey"
              @click="runEffect"
            >
              执行
            </el-button>
          </el-form-item>
        </el-form>
      </template>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import type { OperationVO } from '@/api/pms/platform/businessmodel'
import type { AttemptVO, EffectVO, OpinionVO } from '@/api/pms/platform/approval'
import {
  decideAttempt,
  executeEffect,
  getAttemptDetail,
  listAttempts,
  resubmitAttempt,
  submitApproval,
  withdrawAttempt
} from '@/api/pms/platform/approval'

defineOptions({ name: 'ApprovalPanel' })
const props = defineProps<{
  ownerModule: string
  entityType: string
  entityId: number
  operations?: OperationVO[]
}>()
const emit = defineEmits<{ (e: 'changed'): void }>()

const loading = ref(false)
const submitting = ref(false)
const acting = ref(false)
const panelError = ref('')
const attempts = ref<AttemptVO[]>([])
const selected = ref<AttemptVO>()
const attemptTableRef = ref<{ setCurrentRow: (row?: AttemptVO) => void }>()
const opinions = ref<OpinionVO[]>([])
const effects = ref<EffectVO[]>([])
const comment = ref('')

const submitForm = ref({ purpose: '', submissionBasis: '', neutralProcessRef: '' })
const effectForm = ref({ operationCode: '', idempotencyKey: '' })

// 生效命令候选：目录声明的非创建类可执行操作。
const effectOperations = computed(() =>
  (props.operations ?? []).filter((operation) => operation.kind !== 'CREATE' && operation.executable)
)
const canSubmit = computed(
  () => Boolean(submitForm.value.purpose && submitForm.value.submissionBasis && submitForm.value.neutralProcessRef)
)

const load = async () => {
  loading.value = true
  try {
    attempts.value = (await listAttempts(props.ownerModule, props.entityType, props.entityId)) ?? []
    // 重载后保持选中行：数据替换会触发 current-change(null)，这里恢复高亮并重取详情。
    const keepId = selected.value?.id
    if (keepId) {
      const row = attempts.value.find((attempt) => attempt.id === keepId)
      if (row) {
        await nextTick()
        attemptTableRef.value?.setCurrentRow(row)
      }
    }
  } catch (error: any) {
    panelError.value = error?.response?.data?.msg || error?.message || '审批尝试读取失败'
  } finally {
    loading.value = false
  }
}
const reload = () => load()

const selectAttempt = async (row: AttemptVO | null) => {
  // 数据重载时的 current-change(null) 不清空办理区，避免闪没。
  if (!row) return
  selected.value = row
  try {
    const detail = await getAttemptDetail(row.id)
    opinions.value = detail?.opinions ?? []
    effects.value = detail?.effects ?? []
  } catch (error: any) {
    panelError.value = error?.response?.data?.msg || error?.message || '审批详情读取失败'
  }
}

const submit = async () => {
  submitting.value = true
  panelError.value = ''
  try {
    const created = await submitApproval({
      ownerModule: props.ownerModule,
      entityType: props.entityType,
      entityId: props.entityId,
      purpose: submitForm.value.purpose,
      attemptId: crypto.randomUUID(),
      submissionBasis: submitForm.value.submissionBasis,
      neutralProcessRef: submitForm.value.neutralProcessRef
    })
    emit('changed')
    await load()
    if (created && created.length > 0) {
      const row = attempts.value.find((attempt) => attempt.id === created[0].id)
      if (row) {
        await nextTick()
        attemptTableRef.value?.setCurrentRow(row)
      }
    }
  } catch (error: any) {
    panelError.value = error?.response?.data?.msg || error?.message || '审批发起失败'
  } finally {
    submitting.value = false
  }
}

const act = async (action: () => Promise<unknown>, fallback: string) => {
  acting.value = true
  panelError.value = ''
  try {
    await action()
    emit('changed')
    await load()
  } catch (error: any) {
    panelError.value = error?.response?.data?.msg || error?.message || fallback
  } finally {
    acting.value = false
  }
}

const withdraw = (row: AttemptVO) =>
  act(() => withdrawAttempt(row.id, '发起人在面板撤回'), '撤回失败')
const decide = (approved: boolean) =>
  act(() => decideAttempt(selected.value!.id, approved, comment.value), '审批决定失败')
const resubmit = (row: AttemptVO) =>
  act(
    () => resubmitAttempt(row.id, crypto.randomUUID(), `重提：${row.attemptId}`),
    '重提失败'
  )

const prepareEffect = (row: AttemptVO) => {
  selectAttempt(row)
  effectForm.value = { operationCode: effectOperations.value[0]?.code ?? '', idempotencyKey: crypto.randomUUID() }
}
const runEffect = () =>
  act(
    () =>
      executeEffect(selected.value!.id, {
        operationCode: effectForm.value.operationCode,
        idempotencyKey: effectForm.value.idempotencyKey
      }),
    '生效命令执行失败'
  )

onMounted(load)

const statusLabel = (status: string) =>
  ({ PENDING: '进行中', APPROVED: '已批准', REJECTED: '已驳回', WITHDRAWN: '已撤回' })[status] ?? status
const statusTagType = (status: string): 'warning' | 'success' | 'danger' | 'info' =>
  ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', WITHDRAWN: 'info' } as const)[status] ?? 'info'
const effectStatusLabel = (status: string) =>
  ({ SUCCESS: '成功', PENDING_RECOVERY: '待恢复', FAILED: '失败' })[status] ?? status
</script>
