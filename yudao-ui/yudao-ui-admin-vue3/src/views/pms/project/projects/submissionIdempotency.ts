export interface SubmissionIdempotencyState {
  keyFor: (payload: unknown) => string
  reset: () => void
}

const defaultKeyFactory = () => `pms-${crypto.randomUUID()}`

/** 同一未修改请求复用Key；请求内容变化后自动换Key。状态仅存在于当前页面内存。 */
export const createSubmissionIdempotencyState = (
  keyFactory: () => string = defaultKeyFactory
): SubmissionIdempotencyState => {
  let fingerprint = ''
  let key = ''

  return {
    keyFor(payload: unknown) {
      const nextFingerprint = JSON.stringify(payload)
      if (!key || fingerprint !== nextFingerprint) {
        fingerprint = nextFingerprint
        key = keyFactory()
      }
      return key
    },
    reset() {
      fingerprint = ''
      key = ''
    }
  }
}
