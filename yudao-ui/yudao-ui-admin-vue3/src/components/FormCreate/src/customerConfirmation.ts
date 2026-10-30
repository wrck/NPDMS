import './customerConfirmation.scss'

export const customerFormOption = {
  form: { labelPosition: 'top', size: 'large' },
  submitBtn: false,
  resetBtn: false
}

export const signaturePngFile = (dataUrl: string) => {
  if (!/^data:image\/png;base64,[A-Za-z0-9+/=]+$/.test(dataUrl)) throw new Error('请重新手写签字')
  const bytes = Uint8Array.from(atob(dataUrl.split(',')[1]), (char) => char.charCodeAt(0))
  return new File([bytes], '客户手写签字.png', { type: 'image/png' })
}
