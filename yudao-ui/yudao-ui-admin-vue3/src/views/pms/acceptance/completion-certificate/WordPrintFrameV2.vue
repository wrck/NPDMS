<template>
  <article class="word-print-sheet">
    <header class="word-print-header">
      <img :src="logo" alt="DPtech" />
      <span>{{ title }}</span>
    </header>
    <div class="word-print-watermark" aria-hidden="true">应用超乎想象</div>
    <div class="word-print-body"><slot></slot></div>
    <footer class="word-print-footer">
      <span>版权所有，侵权必究 All rights reserved</span>
      <span>第1页，共1页 Page 1, Total 1</span>
    </footer>
  </article>
</template>

<script setup lang="ts">
import logo from './dptech-print-v2.png'
defineProps<{ title: string }>()
</script>

<style scoped>
.word-print-sheet {
  position: relative;
  box-sizing: border-box;
  width: 794px;
  min-height: 1122px;
  padding: 25mm 20mm 28mm 25mm;
  background: #fff;
  color: #000;
  font-family: SimSun, '宋体', 'Times New Roman', serif;
  font-size: 16px;
  line-height: 1.35;
}
.word-print-header {
  position: absolute;
  top: 8mm;
  left: 25mm;
  right: 20mm;
  height: 12mm;
  border-bottom: 1px solid #000;
  text-align: center;
  font-size: 12px;
}
.word-print-header img { position: absolute; left: 0; top: 0; width: 23.6mm; height: 9mm; }
.word-print-header span { display: block; padding-top: 8mm; }
.word-print-watermark {
  position: absolute;
  top: 134mm;
  left: 0;
  width: 210mm;
  font-size: 116px;
  line-height: 1;
  color: #dedede;
  white-space: nowrap;
  text-align: center;
  transform: rotate(-45deg);
  pointer-events: none;
}
.word-print-body { position: relative; }
.word-print-footer {
  position: absolute;
  bottom: 20mm;
  left: 25mm;
  right: 20mm;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 10px;
  white-space: nowrap;
}
</style>
