<template>
  <section class="difference-panel" aria-labelledby="arrival-difference-title">
    <div class="section-heading">
      <div>
        <h3 id="arrival-difference-title">到货差异</h3>
        <p>历史 revision 只读；处置动作由服务端状态与 allowedActions 决定。</p>
      </div>
    </div>
    <el-empty v-if="!currentDifferences.length" description="当前没有到货差异" :image-size="64" />
    <div v-else class="difference-list">
      <article
        v-for="difference in currentDifferences"
        :key="String(difference.id)"
        class="difference-card"
      >
        <div class="difference-summary">
          <div>
            <strong>差异 #{{ difference.differenceNo }}</strong>
            <span>{{ difference.differenceType }}</span>
          </div>
          <el-tag :type="difference.resolutionStatus === 'OPEN' ? 'danger' : 'info'">
            {{ difference.resolutionStatus }}
          </el-tag>
        </div>
        <p>{{ difference.reason }}</p>
        <div
          v-if="resolutionOptions(difference).length"
          v-hasPermi="['pms:arrival-acceptance:resolve-difference']"
          class="difference-actions"
        >
          <el-button
            v-for="option in resolutionOptions(difference)"
            :key="option.value"
            size="small"
            @click="openResolution(difference, option.value)"
            >{{ option.label }}</el-button
          >
        </div>
      </article>
    </div>

    <Dialog v-model="dialogVisible" title="处理到货差异" width="560px">
      <el-form label-position="top">
        <el-form-item label="处置方式">
          <el-input :model-value="resolutionType" disabled />
        </el-form-item>
        <el-form-item v-if="resolutionType === 'SUPPLEMENT' && quantityScope" label="本次补签数量">
          <el-input-number
            v-model="supplementQuantity"
            :min="0.001"
            :max="quantityScope.quantity"
          />
        </el-form-item>
        <el-form-item v-if="resolutionType === 'EXEMPT'" label="豁免有效期">
          <el-date-picker
            v-model="expiresAt"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            class="!w-full"
          />
        </el-form-item>
        <el-form-item label="原因">
          <el-input v-model="reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-alert v-if="!evidenceRevision" type="warning" :closable="false">
          请先在证据面板上传本次处置证据。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!canSubmit" @click="submitResolution"
          >提交处置</el-button
        >
      </template>
    </Dialog>
  </section>
</template>

<script setup lang="ts">
import type {
  ArrivalDifference,
  FileRevision,
  ResolveDifferenceRequest
} from '@/api/pms/engineering/arrival-acceptance'
import { arrivalResolutionOptions } from '../arrivalAcceptanceInteraction'

const props = defineProps<{
  differences: ArrivalDifference[]
  aggregateStatus: string
  canResolve: boolean
  evidenceRevision: FileRevision | null
}>()
const emit = defineEmits<{ resolve: [value: ResolveDifferenceRequest] }>()
const currentDifferences = computed(() => props.differences.filter((item) => item.current))
const selected = ref<ArrivalDifference>()
const dialogVisible = ref(false)
const resolutionType = ref<'SUPPLEMENT' | 'KEEP_REJECTED' | 'EXEMPT' | 'CLOSE'>('SUPPLEMENT')
const reason = ref('')
const expiresAt = ref('')
const supplementQuantity = ref(0)
const quantityScope = computed(() =>
  selected.value?.scopeSnapshot.scopeType === 'ORDER_MODEL_QUANTITY'
    ? selected.value.scopeSnapshot
    : null
)
const canSubmit = computed(
  () =>
    Boolean(selected.value && props.evidenceRevision && reason.value.trim()) &&
    (resolutionType.value !== 'EXEMPT' || Boolean(expiresAt.value))
)
const labels = {
  SUPPLEMENT: '补签',
  KEEP_REJECTED: '保持拒收',
  EXEMPT: '具体豁免',
  CLOSE: '关闭差异'
} as const
const resolutionOptions = (difference: ArrivalDifference) => {
  if (!props.canResolve) return []
  const values = arrivalResolutionOptions(props.aggregateStatus, difference.resolutionStatus)
  return values.map((value) => ({ value, label: labels[value] }))
}

const openResolution = (
  difference: ArrivalDifference,
  type: 'SUPPLEMENT' | 'KEEP_REJECTED' | 'EXEMPT' | 'CLOSE'
) => {
  selected.value = difference
  resolutionType.value = type
  reason.value = ''
  expiresAt.value = ''
  supplementQuantity.value =
    difference.scopeSnapshot.scopeType === 'ORDER_MODEL_QUANTITY'
      ? difference.scopeSnapshot.quantity
      : 0
  dialogVisible.value = true
}

const submitResolution = () => {
  const difference = selected.value
  const evidenceRevision = props.evidenceRevision
  if (!difference || !evidenceRevision) return
  const common = {
    differenceId: difference.id,
    expectedDifferenceRevision: difference.revisionNo,
    expectedDifferenceVersion: difference.version,
    reason: reason.value.trim(),
    evidenceRevision
  }
  if (resolutionType.value === 'SUPPLEMENT') {
    const scope = difference.scopeSnapshot
    emit('resolve', {
      ...common,
      resolutionType: 'SUPPLEMENT',
      supplementScope:
        scope.scopeType === 'DEVICE' ? scope : { ...scope, quantity: supplementQuantity.value }
    })
  } else if (resolutionType.value === 'EXEMPT') {
    emit('resolve', {
      ...common,
      resolutionType: 'EXEMPT',
      riskDescription: difference.riskDescription || difference.reason,
      expiresAt: expiresAt.value
    })
  } else {
    emit('resolve', { ...common, resolutionType: resolutionType.value })
  }
  dialogVisible.value = false
}
</script>

<style scoped lang="scss">
.difference-panel {
  margin-top: 20px;
}

.section-heading h3,
.section-heading p,
.difference-card p {
  margin: 0;
}

.section-heading p {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.difference-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.difference-card {
  padding: 14px;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

.difference-summary,
.difference-summary > div,
.difference-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.difference-card p,
.difference-actions {
  margin-top: 12px;
}

@media (width <= 767px) {
  .difference-list {
    grid-template-columns: 1fr;
  }
}
</style>
