const extensionMediaTypes: Record<string, string> = {
  txt: 'text/plain', log: 'text/plain', cfg: 'text/plain', conf: 'text/plain', csv: 'text/csv',
  xls: 'application/vnd.ms-excel',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
}

export const fileUploadMediaType = (file: Pick<File, 'name' | 'type'>) => {
  const extension = file.name.split('.').pop()?.toLowerCase() || ''
  // Windows常把CSV报告为Excel，日志/配置文本常没有浏览器MIME；实际内容仍由服务端检测。
  if (extension === 'csv' || !file.type || file.type === 'application/octet-stream')
    return extensionMediaTypes[extension] || file.type || 'application/octet-stream'
  return file.type
}
