let accessToken = ''
let csrfToken = ''
export function setAccessToken(value: string) { accessToken = value }
export class ApiError extends Error { constructor(public status: number, message: string) { super(message) } }
export async function api<T>(path: string, method = 'GET', data?: unknown): Promise<T> {
  if (method !== 'GET') {
    const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
    if (!response.ok) throw new ApiError(response.status, '安全会话暂不可用，请重试')
    csrfToken = (await response.json()).token
  }
  const response = await fetch('/api/v1' + path, {
    method, credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...(accessToken ? { Authorization: 'Bearer ' + accessToken } : {}), ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) },
    body: data === undefined ? undefined : JSON.stringify(data)
  })
  const body = response.status === 204 ? undefined : await response.json()
  if (!response.ok) throw new ApiError(response.status, body?.error?.message ?? '服务暂不可用，请重试')
  return body as T
}
export async function upload<T>(path: string, form: FormData): Promise<T> {
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' })
  if (!response.ok) throw new ApiError(response.status, '安全会话暂不可用，请重试')
  csrfToken = (await response.json()).token
  const result = await fetch('/api/v1' + path, { method: 'POST', credentials: 'same-origin', headers: { ...(accessToken ? { Authorization: 'Bearer ' + accessToken } : {}), ...(csrfToken ? { 'X-XSRF-TOKEN': csrfToken } : {}) }, body: form })
  const body = await result.json()
  if (!result.ok) throw new ApiError(result.status, body?.error?.message ?? '服务暂不可用，请重试')
  return body as T
}
export async function download(path: string): Promise<Blob> {
  const response = await fetch('/api/v1' + path, { credentials: 'same-origin', headers: accessToken ? { Authorization: 'Bearer ' + accessToken } : {} })
  if (!response.ok) { const body = await response.json().catch(() => ({})); throw new ApiError(response.status, body?.error?.message ?? '附件下载失败') }
  return response.blob()
}
