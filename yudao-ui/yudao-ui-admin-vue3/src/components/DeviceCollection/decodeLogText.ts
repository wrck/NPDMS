/** Strict decoding for both log upload entrances; never discard malformed bytes. */
export const decodeLogText = (bytes: Uint8Array): string => {
  if (bytes[0] === 0xff && bytes[1] === 0xfe)
    return new TextDecoder('utf-16le', { fatal: true }).decode(bytes)
  if (bytes[0] === 0xfe && bytes[1] === 0xff)
    return new TextDecoder('utf-16be', { fatal: true }).decode(bytes)
  if (bytes[0] === 0xef && bytes[1] === 0xbb && bytes[2] === 0xbf)
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  try {
    return new TextDecoder('utf-8', { fatal: true }).decode(bytes)
  } catch {
    return new TextDecoder('gb18030', { fatal: true }).decode(bytes)
  }
}
