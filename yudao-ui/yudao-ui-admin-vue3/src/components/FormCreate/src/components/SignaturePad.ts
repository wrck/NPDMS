import { defineComponent, nextTick } from 'vue'
import FcDesigner from '@form-create/designer'
import formCreate from '@form-create/element-ui'
import './signaturePad.scss'

// Preserve FormCreate's built-in signature actions and PNG value contract.
const native = FcDesigner.formCreate.component('signaturePad')
export const SignaturePad = defineComponent({
  name: 'FormCreateSignaturePad',
  extends: native,
  methods: {
    async open(this: any) {
      if (this.disabled) return
      this.visible = true
      await nextTick()
      // The native visibility watcher creates the drawing instance in its own nextTick.
      await nextTick()
      const canvas = this.$refs.pad as HTMLCanvasElement | undefined
      if (!canvas) return
      const mobile = window.matchMedia('(pointer: coarse) and (max-width: 1024px)').matches
      const width = document.documentElement.clientWidth
      const height = window.visualViewport?.height ?? window.innerHeight
      canvas.closest('.el-dialog')?.classList.add('form-create-signature-dialog')
      canvas.width = mobile ? Math.max(200, Math.max(width, height) - 56) : Math.min(600, Math.max(200, width - 72))
      canvas.height = mobile ? Math.max(100, Math.min(width, height) - 156) : 270
      // signature_pad subtracts the bounding rectangle, which does not undo CSS rotation.
      // Adapt the point returned by the installed native control, retaining pressure/time.
      const pad = this.signaturePad
      const createPoint = pad._createPoint.bind(pad)
      pad._createPoint = (x: number, y: number, pressure: number) => {
        const point = createPoint(x, y, pressure)
        const rect = canvas.getBoundingClientRect()
        const rotated = window.matchMedia('(pointer: coarse) and (max-width: 1024px) and (orientation: portrait)').matches
        point.x = rotated ? (y - rect.top) * canvas.width / rect.height : (x - rect.left) * canvas.width / rect.width
        point.y = rotated ? (rect.right - x) * canvas.height / rect.width : (y - rect.top) * canvas.height / rect.height
        return point
      }
      this.signaturePad?.clear()
    }
  }
})

/** Register the same control in runtime forms and designer previews. */
export const registerSignaturePad = () => {
  formCreate.component('signaturePad', SignaturePad)
  FcDesigner.formCreate.component('signaturePad', SignaturePad)
}
