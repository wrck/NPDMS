<template>
  <Teleport to="body">
    <Dialog
      v-model="visible"
      title="采集日志结构化解析"
      aria-label="采集日志结构化解析"
      width="min(1180px, 96vw)"
    >
      <LogParsingPanel v-if="visible && source" :key="String(source.executionId)" v-bind="source" />
      <template #footer><el-button @click="visible = false">关闭</el-button></template>
    </Dialog>
  </Teleport>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { context, type Entry, type Id } from '@/api/pms/platform/deviceCollection'
import LogParsingPanel from './LogParsingPanel.vue'
import { useMessage } from '@/hooks/web/useMessage'
const message = useMessage()
const visible = ref(false)
const source = ref<{ projectId: Id; entry: Entry; objectId: Id; executionId: Id }>()
let generation = 0
const open = async (entry: Entry, objectId: Id, executionId: Id) => {
  const currentGeneration = ++generation
  try {
    const current = await context(entry, objectId)
    if (currentGeneration !== generation) return
    source.value = { projectId: current.projectId, entry, objectId, executionId }
    visible.value = true
  } catch {
    if (currentGeneration === generation) message.error('无法读取日志所属项目，请刷新后重试')
  }
}
onBeforeUnmount(() => {
  generation++
})
defineExpose({ open })
</script>
