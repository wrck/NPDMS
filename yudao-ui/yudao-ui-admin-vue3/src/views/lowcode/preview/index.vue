<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { get } from '@/utils/request'

defineOptions({ name: 'LowCodePreviewView' })

const route = useRoute()
// Preview receives configType (FORM/LIST/TAB/RELATED_PAGE) and configCode (string code)
// via query params from the designer's "preview" button.
const configType = ref((route.query.configType as string) || 'FORM')
const configCode = ref((route.query.configCode as string) || '')
const device = ref<'pc' | 'tablet' | 'mobile'>('pc')
const orientation = ref<'portrait' | 'landscape'>('portrait')

const deviceSize = computed(() => {
  const sizes = {
    pc: { width: 1920, height: 1080 },
    tablet: { width: 768, height: 1024 },
    mobile: { width: 375, height: 812 }
  }
  const s = sizes[device.value]
  return orientation.value === 'landscape' && device.value !== 'pc'
    ? { width: s.height, height: s.width }
    : s
})

// Map configType (uppercase) to render route pageType (lowercase)
const pageTypeMap: Record<string, string> = {
  FORM: 'form',
  LIST: 'list',
  TAB: 'tab',
  RELATED_PAGE: 'related-page'
}

const previewUrl = computed(() => {
  const pt = pageTypeMap[configType.value] || configType.value.toLowerCase()
  // Use the render route path format: /lowcode/:pageType/:pageCode
  return `/lowcode/${pt}/${configCode.value}?preview=true`
})
</script>

<template>
  <div class="preview-container">
    <div class="preview-toolbar">
      <el-radio-group v-model="device" size="small">
        <el-radio-button value="pc">PC</el-radio-button>
        <el-radio-button value="tablet">平板</el-radio-button>
        <el-radio-button value="mobile">手机</el-radio-button>
      </el-radio-group>
      <el-radio-group v-if="device !== 'pc'" v-model="orientation" size="small" style="margin-left: 12px">
        <el-radio-button value="portrait">竖屏</el-radio-button>
        <el-radio-button value="landscape">横屏</el-radio-button>
      </el-radio-group>
      <span style="margin-left: 12px; color: #909399; font-size: 12px">
        {{ deviceSize.width }} × {{ deviceSize.height }}
      </span>
    </div>
    <div
      class="preview-stage"
      :style="{
        width: deviceSize.width + 'px',
        height: deviceSize.height + 'px',
        maxWidth: '100%',
        maxHeight: 'calc(100vh - 140px)'
      }"
    >
      <iframe
        :src="previewUrl"
        :style="{
          width: deviceSize.width + 'px',
          height: deviceSize.height + 'px',
          border: '1px solid #dcdfe6',
          transformOrigin: 'top left'
        }"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.preview-container {
  padding: 16px;
}
.preview-toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
}
.preview-stage {
  margin: 0 auto;
  overflow: auto;
  background: #f5f5f5;
  border-radius: 4px;
}
</style>
