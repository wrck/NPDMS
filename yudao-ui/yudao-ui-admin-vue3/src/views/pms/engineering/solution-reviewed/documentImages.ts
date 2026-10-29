// Resolve before DOCX conversion so an inaccessible image cannot silently disappear.
export const prepareDocumentImages = async (urls: string[]): Promise<Record<string, string>> => {
  const { Buffer } = await import('buffer/')
  const images = await Promise.all([...new Set(urls)].map(async (url) => {
    const response = await fetch(url)
    const contentType = response.headers.get('content-type')?.split(';')[0] || ''
    if (!response.ok || !contentType.startsWith('image/')) {
      throw new Error('方案图片读取失败，请确认拓扑附件可访问后重试。')
    }
    const data = Buffer.from(await response.arrayBuffer()).toString('base64')
    return [url, `data:${contentType};base64,${data}`] as const
  }))
  return Object.fromEntries(images)
}
