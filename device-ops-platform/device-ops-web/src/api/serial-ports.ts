import { deviceOpsApi } from '@/api/device-ops'

export interface SerialPortList {
  ports: string[]
}

export function listSerialPorts(signal?: AbortSignal): Promise<string[]> {
  return deviceOpsApi
    .get<SerialPortList>('serial-ports', { signal })
    .then(({ data }) => data.ports)
}
