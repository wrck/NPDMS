<template>
  <nav class="inspection-progress" aria-label="巡检办理步骤">
    <ol>
      <li v-for="(label, index) in labels" :key="label" :class="stepClass(index)">
        <button
          type="button"
          :disabled="!interactive"
          :aria-current="index === active ? 'step' : undefined"
          :aria-label="`${index + 1}. ${label}${index < reached ? '，已完成' : index === reached ? '，当前环节' : '，待办理'}`"
          @click="$emit('select', index)"
        >
          <span class="inspection-progress__circle" aria-hidden="true">
            <Icon v-if="index < reached" icon="ep:check" :size="15" />
            <template v-else>{{ index + 1 }}</template>
          </span>
          <span>{{ label }}</span>
        </button>
        <span
          v-if="index < labels.length - 1"
          class="inspection-progress__divider"
          aria-hidden="true"
        ></span>
      </li>
    </ol>
  </nav>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{ active: number; reached: number; interactive?: boolean }>(),
  {
    interactive: true
  }
)
defineEmits<{ select: [index: number] }>()
const labels = ['巡检准备', '巡检执行', '报告生成', '待办跟踪', '闭环']
const stepClass = (index: number) => ({
  done: index < props.reached,
  current: index === props.reached,
  pending: index > props.reached,
  selected: index === props.active
})
</script>

<style scoped>
.inspection-progress {
  margin-bottom: 20px;
  padding: 16px 24px;
  overflow-x: auto;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgb(0 0 0 / 4%);
}
ol {
  display: flex;
  min-width: 650px;
  align-items: center;
  margin: 0;
  padding: 0;
  list-style: none;
}
li {
  display: flex;
  flex: 1;
  align-items: center;
  min-width: 0;
}
li:last-child {
  flex: 0 0 auto;
}
button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}
button:disabled {
  cursor: default;
  opacity: 1;
}
button:focus-visible {
  outline: 2px solid #1677ff;
  outline-offset: 4px;
  border-radius: 4px;
}
.inspection-progress__circle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  border: 2px solid #ff4d4f;
  border-radius: 50%;
  color: #ff4d4f;
  font-size: 12px;
  font-weight: 700;
}
.inspection-progress__divider {
  flex: 1;
  height: 2px;
  margin: 0 8px;
  background: #e8ecf1;
  border-radius: 1px;
}
.done {
  color: #389e0d;
}
.done .inspection-progress__circle {
  color: #fff;
  background: #52c41a;
  border-color: #52c41a;
}
.done .inspection-progress__divider {
  background: #52c41a;
}
.current {
  color: #1677ff;
  font-weight: 600;
}
.current .inspection-progress__circle {
  color: #fff;
  background: #1677ff;
  border-color: #1677ff;
  box-shadow: 0 0 0 4px rgb(22 119 255 / 15%);
}
.pending {
  color: #c93638;
}
.selected button {
  font-weight: 600;
}
.selected:not(.current) button > span:last-child {
  text-decoration: underline;
  text-underline-offset: 5px;
}
@media (max-width: 767px) {
  .inspection-progress {
    padding: 12px 16px;
  }
}
</style>
