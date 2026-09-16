<template>
  <el-dialog v-model="visible" title="配置关联" width="500px" @close="onClose">
    <el-form :model="form" label-width="100px">
      <el-form-item label="关联类型">
        <el-select v-model="form.relationType" placeholder="选择关联类型">
          <el-option label="一对一" value="ONE_TO_ONE" />
          <el-option label="一对多" value="ONE_TO_MANY" />
          <el-option label="多对一" value="MANY_TO_ONE" />
          <el-option label="多对多" value="MANY_TO_MANY" />
        </el-select>
      </el-form-item>
      <el-form-item label="外键字段">
        <el-input v-model="form.fromFieldName" placeholder="如 user_id" />
      </el-form-item>
      <el-form-item v-if="form.relationType === 'MANY_TO_MANY'" label="反向字段">
        <el-input v-model="form.toFieldName" placeholder="如 role_id" />
      </el-form-item>
      <el-form-item label="级联策略">
        <el-select v-model="form.onDelete" placeholder="选择级联策略">
          <el-option label="级联删除 (CASCADE)" value="CASCADE" />
          <el-option label="置空 (SET_NULL)" value="SET_NULL" />
          <el-option label="禁止 (RESTRICT)" value="RESTRICT" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="onConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import type { LowCodeRelation } from '@/api/lowcode-entity'

const props = defineProps<{ modelValue: boolean; fromEntityId: number; toEntityId: number }>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'confirm', relation: LowCodeRelation): void
}>()

const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => { visible.value = v })
watch(visible, (v) => emit('update:modelValue', v))

const form = ref<LowCodeRelation>({
  fromEntityId: 0,
  toEntityId: 0,
  relationType: 'ONE_TO_MANY',
  fromFieldName: '',
  toFieldName: '',
  onDelete: 'RESTRICT',
  onUpdate: 'RESTRICT'
})

watch(() => props.fromEntityId, (v) => { form.value.fromEntityId = v })
watch(() => props.toEntityId, (v) => { form.value.toEntityId = v })

function onConfirm() {
  emit('confirm', { ...form.value })
  visible.value = false
}
function onClose() {
  emit('update:modelValue', false)
}
</script>
