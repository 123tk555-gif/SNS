// サーバーの API を呼ぶ。データを変える操作(POST / PUT / DELETE)には、
// CSRF 対策の値(Cookie XSRF-TOKEN)をヘッダー X-XSRF-TOKEN に入れて送る(機能定義書 1.2)

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? 'エラーが発生しました。もう一度お試しください')
    this.status = status
    this.errors = body?.errors ?? []
  }

  /** 入力チェックのエラーのうち、その項目のメッセージ */
  fieldError(field) {
    return this.errors.find((e) => e.field === field)?.message
  }
}

function readCookie(name) {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
  return match ? decodeURIComponent(match[1]) : null
}

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    const token = readCookie('XSRF-TOKEN')
    if (token) headers['X-XSRF-TOKEN'] = token
  }

  const options = { method, headers, credentials: 'same-origin' }
  if (body !== undefined) options.body = JSON.stringify(body)

  let response
  try {
    response = await fetch(path, options)
  } catch {
    throw new ApiError(0, { message: 'サーバーに接続できませんでした。しばらくしてからもう一度お試しください' })
  }

  if (response.status === 204) return null
  const data = await response.json().catch(() => null)
  if (!response.ok) throw new ApiError(response.status, data)
  return data
}
