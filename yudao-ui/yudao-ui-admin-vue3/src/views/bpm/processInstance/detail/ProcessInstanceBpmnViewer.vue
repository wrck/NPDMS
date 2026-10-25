<template>
  <el-card v-loading="loading" class="box-card" :class="{ 'closure-layout': diagram.generated }">
    <el-alert v-if="diagram.error" :title="diagram.error" type="info" :closable="false" />
    <MyProcessViewer v-else key="designer" :xml="diagram.xml" :view="view" class="process-viewer" />
  </el-card>
</template>
<script lang="ts" setup>
import { propTypes } from '@/utils/propTypes'
import { MyProcessViewer } from '@/components/bpmnProcessDesigner/package'
import { normalClosureDiagram } from './normalClosureDiagram'

defineOptions({ name: 'BpmProcessInstanceBpmnViewer' })

const props = defineProps({
  loading: propTypes.bool.def(false), // 是否加载中
  bpmnXml: propTypes.string.def(''), // BPMN XML
  modelView: propTypes.object
})

const view = computed(() => ({
  ...props.modelView,
  bpmnXml: props.bpmnXml || props.modelView?.bpmnXml || ''
}))
const diagram = computed(() => normalClosureDiagram(view.value.bpmnXml))
</script>
<style lang="scss" scoped>
.closure-layout :deep(.djs-label) {
  fill: var(--el-text-color-primary) !important;
}
.box-card {
  width: 100%;
  height: 100%;
  margin-bottom: 0;

  :deep(.el-card__body) {
    height: 100%;
    padding: 0;
  }

  :deep(.process-viewer) {
    width: 100%;
    height: 100% !important;
    min-height: 100%;
    overflow: auto;
  }
}
</style>
