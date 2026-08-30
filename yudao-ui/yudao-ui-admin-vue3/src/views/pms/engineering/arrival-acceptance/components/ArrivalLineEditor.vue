<template>
  <section class="line-editor" aria-labelledby="arrival-line-title">
    <div class="section-heading">
      <div>
        <h3 id="arrival-line-title">到货明细</h3>
        <p>按设备序列或订单型号数量记录，本页不推导应到范围。</p>
      </div>
      <el-button v-if="editable" data-testid="append-line" @click="appendLine">新增明细</el-button>
    </div>

    <el-empty v-if="!modelValue.length" description="尚未录入到货明细" :image-size="72" />
    <div v-else class="line-list">
      <article
        v-for="(line, index) in modelValue"
        :key="`${line.scopeType}-${index}`"
        class="line-row"
      >
        <el-select
          :model-value="line.scopeType"
          :disabled="!editable || Boolean(line.lineId)"
          aria-label="明细范围类型"
          @change="changeType(index, $event)"
        >
          <el-option label="设备序列" value="DEVICE" />
          <el-option label="订单型号数量" value="ORDER_MODEL_QUANTITY" />
        </el-select>
        <template v-if="line.scopeType === 'DEVICE'">
          <el-input
            :model-value="String(line.deviceId || '')"
            :disabled="!editable || Boolean(line.lineId)"
            aria-label="设备ID"
            placeholder="设备ID"
            @update:model-value="updateDevice(index, $event)"
          />
          <el-switch
            :model-value="line.received"
            :disabled="!editable"
            active-text="已签收"
            inactive-text="未到货"
            @update:model-value="updateReceived(index, Boolean($event))"
          />
        </template>
        <template v-else>
          <el-input
            :model-value="String(line.orderLineId || '')"
            :disabled="!editable || Boolean(line.lineId)"
            aria-label="订单行ID"
            placeholder="订单行ID"
            @update:model-value="updateQuantityLine(index, 'orderLineId', $event)"
          />
          <el-input
            :model-value="line.productCode || ''"
            :disabled="!editable || Boolean(line.lineId)"
            aria-label="产品编码"
            placeholder="产品编码"
            @update:model-value="updateQuantityLine(index, 'productCode', $event)"
          />
          <el-input
            :model-value="line.modelCode || ''"
            :disabled="!editable || Boolean(line.lineId)"
            aria-label="型号编码"
            placeholder="型号编码"
            @update:model-value="updateQuantityLine(index, 'modelCode', $event)"
          />
          <el-input-number
            :model-value="line.acceptedQuantity"
            :disabled="!editable"
            :min="0"
            :precision="3"
            aria-label="已签收数量"
            @update:model-value="updateQuantityLine(index, 'acceptedQuantity', $event || 0)"
          />
          <el-input
            :model-value="line.unitCode"
            :disabled="!editable || Boolean(line.lineId)"
            aria-label="计量单位"
            placeholder="单位"
            @update:model-value="updateQuantityLine(index, 'unitCode', $event)"
          />
        </template>
        <el-button v-if="editable && !line.lineId" link type="danger" @click="removeLine(index)">
          移除
        </el-button>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ArrivalDraftLine } from '@/api/pms/engineering/arrival-acceptance'

const props = defineProps<{ modelValue: ArrivalDraftLine[]; editable: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: ArrivalDraftLine[]] }>()

const replace = (index: number, value: ArrivalDraftLine) => {
  const lines = [...props.modelValue]
  lines[index] = value
  emit('update:modelValue', lines)
}

const appendLine = () =>
  emit('update:modelValue', [
    ...props.modelValue,
    {
      scopeType: 'DEVICE',
      lineId: null,
      expectedLineVersion: null,
      deviceId: '',
      received: false
    }
  ])

const removeLine = (index: number) =>
  emit(
    'update:modelValue',
    props.modelValue.filter((_, current) => current !== index)
  )

const changeType = (index: number, scopeType: string) => {
  replace(
    index,
    scopeType === 'DEVICE'
      ? {
          scopeType: 'DEVICE',
          lineId: null,
          expectedLineVersion: null,
          deviceId: '',
          received: false
        }
      : {
          scopeType: 'ORDER_MODEL_QUANTITY',
          lineId: null,
          expectedLineVersion: null,
          orderLineId: '',
          productCode: null,
          modelCode: null,
          acceptedQuantity: 0,
          unitCode: ''
        }
  )
}

const updateDevice = (index: number, deviceId: string) => {
  const line = props.modelValue[index]
  if (line.scopeType === 'DEVICE') replace(index, { ...line, deviceId })
}

const updateReceived = (index: number, received: boolean) => {
  const line = props.modelValue[index]
  if (line.scopeType === 'DEVICE') replace(index, { ...line, received })
}

const updateQuantityLine = (index: number, field: string, value: string | number) => {
  const line = props.modelValue[index]
  if (line.scopeType === 'ORDER_MODEL_QUANTITY') replace(index, { ...line, [field]: value })
}
</script>

<style scoped lang="scss">
.line-editor {
  margin-top: 20px;
}

.section-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.section-heading h3,
.section-heading p {
  margin: 0;
}

.section-heading p {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.line-list {
  display: grid;
  gap: 12px;
}

.line-row {
  display: grid;
  grid-template-columns: minmax(140px, 0.8fr) repeat(4, minmax(120px, 1fr)) auto;
  gap: 10px;
  align-items: center;
  padding: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
}

@media (width <= 1023px) {
  .line-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 767px) {
  .section-heading,
  .line-row {
    display: grid;
    grid-template-columns: 1fr;
  }
}
</style>
