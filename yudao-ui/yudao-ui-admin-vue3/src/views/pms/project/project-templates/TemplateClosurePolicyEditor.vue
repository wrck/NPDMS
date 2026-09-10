<template>
  <section aria-label="专用最小正常闭环配置">
    <el-checkbox :model-value="enabled" :disabled="readonly" @update:model-value="setEnabled">
      启用专用闭环（最小正常闭环）
    </el-checkbox>
    <p class="policy-hint">仅用于已批准的 S0 双主责 → S1 工勘测试场景；不自动配置阶段、任务或首次指派角色。</p>
    <p v-if="!enabled" class="policy-hint">未启用不代表条件全部满足。旧模板仍可保存、发布，按此模板新建的项目不具备该闭环能力。</p>
    <template v-else>
      <el-alert title="固定规则：到达模板最后实际阶段、全部任务完成，并重新核验业务事实；任一条件不可手工豁免。" type="info" :closable="false" />
      <p class="policy-hint">人工审批顺序：当前主责服务经理 → 显式材料审核人。全部审批通过后正常闭环，保留 S1，不补造 S6 或业务完成事实。</p>
      <el-form label-position="top" :disabled="readonly">
        <el-form-item label="材料审核人（必选，无默认值）" :error="reviewerError">
          <el-select :model-value="content.closurePolicy?.reviewerUserId" filterable clearable :loading="loading" placeholder="请选择材料审核人" @update:model-value="setReviewer" @visible-change="loadOnOpen">
            <el-option v-if="selectedMissing" :value="content.closurePolicy!.reviewerUserId" :label="`已选用户 ${content.closurePolicy!.reviewerUserId}（发布时核验资格）`" />
            <el-option v-for="user in users" :key="user.id" :value="user.id" :label="`${user.nickname}（${user.id}）`" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
      <p class="policy-hint">用户列表仅供选择，不代表具有审核资格。发布时由服务端核验：同租户、启用、明确获授材料审核权限；超级管理员身份不代替显式授权，并核验真实两节点人工 BPM 定义。</p>
      <p class="policy-hint">规则版本 1 · PMS_MINIMAL_NORMAL_CLOSURE。项目创建时冻结此配置；后续模板调整不改变既有项目。</p>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, ref } from 'vue'
import { getSimpleUserList } from '@/api/system/user'
import type { TemplateDefinitionContent } from '@/api/pms/project/project-templates'
import { errorText } from './editorModel'
const props = defineProps<{ content: TemplateDefinitionContent; readonly?: boolean }>()
const users = ref<Array<{ id: number | string; nickname: string }>>([])
const loading = ref(false)
const loadError = ref('')
const enabled = computed(() => props.content.closurePolicy != null)
const reviewerError = computed(() => enabled.value && !props.content.closurePolicy?.reviewerUserId ? '请选择材料审核人后保存；不会自动指定审核人。' : '')
const selectedMissing = computed(() => !!props.content.closurePolicy?.reviewerUserId && !users.value.some((user) => String(user.id) === String(props.content.closurePolicy?.reviewerUserId)))
const setEnabled = (value: unknown) => {
  if (props.readonly) return
  if (value !== true) { props.content.closurePolicy = null; return }
  if (props.content.closurePolicy) return
  props.content.closurePolicy = {
    closureType: 'NORMAL', ruleRevision: 1, requireTerminalStage: true,
    requireAllTasksDone: true, revalidateBusinessFacts: true,
    processDefinitionKey: 'PMS_MINIMAL_NORMAL_CLOSURE', reviewerUserId: ''
  }
}
const setReviewer = (value: number | string | undefined) => {
  if (props.readonly || !props.content.closurePolicy) return
  props.content.closurePolicy.reviewerUserId = value ?? ''
}
const loadOnOpen = async (open: boolean) => {
  if (!open || props.readonly || loading.value) return
  loading.value = true; loadError.value = ''
  try {
    // This public list supplies identities only. Explicit qualification stays with the server Owner.
    users.value = (await getSimpleUserList()).filter((user) => user.status == null || user.status === 0)
  } catch (error) { loadError.value = `${errorText(error)} 已选审核人保持不变。` }
  finally { loading.value = false }
}
</script>
<style scoped>
.policy-hint { color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.7; margin: 10px 0 16px; }
.el-select { width: 100%; max-width: 480px; }
</style>
