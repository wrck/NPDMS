<template>
  <section aria-label="专用最小正常闭环配置">
    <el-checkbox :model-value="enabled" :disabled="readonly" @update:model-value="setEnabled">
      启用专用闭环（最小正常闭环）
    </el-checkbox>
    <p class="policy-hint">闭环配置直接属于 DesignerDocument；不会生成阶段、任务或规则修订。</p>
    <p v-if="!enabled" class="policy-hint">未启用不代表条件满足；发布快照中不会获得该闭环能力。</p>
    <template v-else>
      <el-alert title="固定规则：到达模板最后实际阶段、全部任务完成，并重新核验业务事实；任一条件不可手工豁免。" type="info" :closable="false" />
      <p class="policy-hint">人工审批顺序：当前主责服务经理 → 显式材料审核人。全部审批通过后正常闭环。</p>
      <el-form label-position="top" :disabled="readonly">
        <el-form-item label="材料审核人（必选，无默认值）" :error="reviewerError">
          <el-select :model-value="reviewerUserId" filterable clearable :loading="loading" placeholder="请选择材料审核人" @update:model-value="setReviewer" @visible-change="loadOnOpen">
            <el-option v-if="selectedMissing" :value="reviewerUserId" :label="`已选用户 ${reviewerUserId}（发布时核验资格）`" />
            <el-option v-for="user in users" :key="user.id" :value="user.id" :label="`${user.nickname}（${user.id}）`" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
      <p class="policy-hint">用户列表只提供身份选择。最终资格与 BPM 定义由服务端发布时重新校验。</p>
      <p class="policy-hint">规则版本 1 · PMS_MINIMAL_NORMAL_CLOSURE。项目创建时冻结发布快照；后续模板调整不改变既有项目。</p>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { getSimpleUserList } from '@/api/system/user'
import type { TemplateDesignerDocument, TemplateClosurePolicy } from '@/api/pms/project/project-templates'
import { errorText } from './editorModel'

const props = defineProps<{ content: TemplateDesignerDocument; readonly?: boolean }>()
const users = ref<Array<{ id: number | string; nickname: string }>>([])
const loading = ref(false)
const loadError = ref('')
const policy = computed(() => props.content.closurePolicy as TemplateClosurePolicy | null | undefined)
const enabled = computed(() => policy.value != null)
const reviewerUserId = computed(() => policy.value?.reviewerUserId)
const reviewerError = computed(() => enabled.value && !reviewerUserId.value ? '请选择材料审核人后保存；不会自动指定审核人。' : '')
const selectedMissing = computed(() => !!reviewerUserId.value && !users.value.some((user) => String(user.id) === String(reviewerUserId.value)))
const setEnabled = (value: unknown) => {
  if (props.readonly) return
  if (value !== true) { props.content.closurePolicy = null; return }
  if (props.content.closurePolicy) return
  props.content.closurePolicy = {
    closureType: 'NORMAL', ruleRevision: 1, requireTerminalStage: true,
    requireAllTasksDone: true, revalidateBusinessFacts: true,
    processDefinitionKey: 'PMS_MINIMAL_NORMAL_CLOSURE', reviewerUserId: ''
  } as TemplateClosurePolicy
}
const setReviewer = (value: number | string | undefined) => {
  if (props.readonly || !policy.value) return
  policy.value.reviewerUserId = value ?? ''
}
const loadOnOpen = async (open: boolean) => {
  if (!open || props.readonly || loading.value) return
  loading.value = true; loadError.value = ''
  try { users.value = (await getSimpleUserList()).filter((user) => user.status == null || user.status === 0) }
  catch (error) { loadError.value = `${errorText(error)} 已选审核人保持不变。` }
  finally { loading.value = false }
}
</script>
<style scoped>
.policy-hint { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; margin: 10px 0 16px; }
.el-select { width: 100%; max-width: 480px; }
</style>