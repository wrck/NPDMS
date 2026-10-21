<template>
  <div ref="container" class="pdf-preview" :aria-busy="busy">
    <div class="pdf-toolbar">
      <el-button :disabled="busy || pageNumber <= 1" @click="showPage(pageNumber - 1)">上一页</el-button>
      <span aria-live="polite">第 {{ pageNumber }} / {{ pageCount }} 页</span>
      <el-button :disabled="busy || pageNumber >= pageCount" @click="showPage(pageNumber + 1)">下一页</el-button>
    </div>
    <div class="pdf-pages">
      <div v-if="busy" role="status">正在渲染文档…</div>
      <canvas ref="canvas" :aria-label="`第 ${pageNumber} 页`" role="img"></canvas>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useResizeObserver } from '@vueuse/core'
import type { PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask } from 'pdfjs-dist'
import workerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'

const props = defineProps<{ file: Blob }>()
const emit = defineEmits<{ error: [] }>()
const container = ref<HTMLElement>()
const canvas = ref<HTMLCanvasElement>()
const busy = ref(true)
const pageNumber = ref(1)
const pageCount = ref(0)
let loadingTask: PDFDocumentLoadingTask | undefined
let document: PDFDocumentProxy | undefined
let renderTask: RenderTask | undefined
let active = true
let generation = 0

const showPage = async (number: number) => {
  if (!document || number < 1 || number > document.numPages) return
  const current = ++generation
  const previousRender = renderTask
  previousRender?.cancel()
  busy.value = true
  try {
    // A canvas must finish its previous render before it can be used again.
    await previousRender?.promise.catch(() => {})
    const page = await document.getPage(number)
    if (!active || current !== generation || !canvas.value) return
    const viewport = page.getViewport({ scale: 1 })
    const availableWidth = Math.max(160, (container.value?.clientWidth || 800) - 32)
    const scale = Math.min(availableWidth / viewport.width, 1.5)
    const scaled = page.getViewport({ scale })
    const outputScale = Math.min(window.devicePixelRatio || 1, 2)
    canvas.value.width = Math.ceil(scaled.width * outputScale)
    canvas.value.height = Math.ceil(scaled.height * outputScale)
    canvas.value.style.width = `${Math.floor(scaled.width)}px`
    canvas.value.style.height = `${Math.floor(scaled.height)}px`
    pageNumber.value = number
    // Local PDF.js rendering works in browsers without a native PDF plugin.
    // https://mozilla.github.io/pdf.js/examples/
    renderTask = page.render({
      canvas: canvas.value,
      viewport: scaled,
      transform: outputScale === 1 ? undefined : [outputScale, 0, 0, outputScale, 0, 0]
    })
    await renderTask.promise
  } catch {
    if (active && current === generation) emit('error')
  } finally {
    if (active && current === generation) busy.value = false
  }
}

onMounted(async () => {
  try {
    const [pdfjs, data] = await Promise.all([import('pdfjs-dist'), props.file.arrayBuffer()])
    if (!active) return
    pdfjs.GlobalWorkerOptions.workerSrc = workerUrl
    loadingTask = pdfjs.getDocument({ data: new Uint8Array(data), stopAtErrors: true })
    document = await loadingTask.promise
    if (!active) return
    pageCount.value = document.numPages
    await showPage(1)
  } catch {
    if (active) emit('error')
  }
})

useResizeObserver(container, () => {
  if (document) void showPage(pageNumber.value)
})
onBeforeUnmount(() => {
  active = false
  generation++
  renderTask?.cancel()
  void loadingTask?.destroy()
})
</script>

<style scoped lang="scss">
.pdf-preview {
  display: flex;
  height: 100%;
  min-width: 0;
  flex-direction: column;
}

.pdf-toolbar {
  display: flex;
  padding: 8px;
  background: var(--el-fill-color-blank);
  border-bottom: 1px solid var(--el-border-color-lighter);
  gap: 12px;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.pdf-pages {
  min-height: 0;
  padding: 16px;
  overflow: auto;
  text-align: center;
  flex: 1;

  canvas {
    display: block;
    margin: 0 auto;
    background: var(--el-color-white);
    box-shadow: var(--el-box-shadow-light);
  }
}
</style>
