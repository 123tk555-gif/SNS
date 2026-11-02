import { api } from './api.js'

// ミュート・ブロック(F-BLOCK-01〜03)。変えたら画面全体に知らせ、開いている一覧から相手の投稿を消す
export const RELATION_CHANGED = 'sns:relation-changed' // detail: { userId, handle, kind: 'mute' | 'block', on }

export async function setRelation(user, kind, on) {
  await api(`/api/users/${encodeURIComponent(user.handle)}/${kind}`, { method: on ? 'PUT' : 'DELETE' })
  window.dispatchEvent(new CustomEvent(RELATION_CHANGED, { detail: { userId: user.id, handle: user.handle, kind, on } }))
}

/** お知らせ欄に出す文(画面仕様定義書 S-07) */
export function relationMessage(handle, kind, on) {
  if (kind === 'mute') {
    return on
      ? `@${handle} さんをミュートしました。このユーザーの投稿はタイムラインに表示されません`
      : `@${handle} さんのミュートを解除しました`
  }
  return on ? `@${handle} さんをブロックしました` : `@${handle} さんのブロックを解除しました`
}
