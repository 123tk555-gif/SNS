import { useEffect, useRef, useState } from 'react'
import { api } from '../api.js'
import { useAuth } from '../auth.jsx'
import { MAX_IMAGES, prepareImage } from '../images.js'
import { useNotice } from '../notice.jsx'
import Avatar from './Avatar.jsx'
import Icon from './Icon.jsx'

const MAX_TEXT = 1000

/** 投稿できたことを画面全体に知らせる(タイムラインがこれを受けて先頭に足す) */
export const POST_CREATED = 'sns:post-created'

/**
 * 投稿欄(画面仕様定義書 D-01)。本文・画像(最大 10 枚)・画像ごとの説明を入力して投稿する。
 * id: 入力欄の id を他の投稿欄と分けるための名前(home / dialog)
 */
export default function Composer({ id, onPosted }) {
  const { user } = useAuth()
  const notify = useNotice()
  const [text, setText] = useState('')
  const [images, setImages] = useState([]) // { file, url, alt }
  const [error, setError] = useState('')
  const [preparing, setPreparing] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const fileInput = useRef(null)
  const latestImages = useRef(images)
  latestImages.current = images

  // 画面を離れるとき、プレビュー用に作った URL を片付ける
  useEffect(() => () => latestImages.current.forEach((image) => URL.revokeObjectURL(image.url)), [])

  const left = MAX_TEXT - [...text.trim()].length
  const canSubmit = !submitting && !preparing && left >= 0 && (text.trim() !== '' || images.length > 0)

  async function addFiles(files) {
    const room = MAX_IMAGES - images.length
    const errors = []
    if (files.length > room) errors.push('画像は10枚までです')
    setPreparing(true)
    const added = []
    for (const file of files.slice(0, Math.max(room, 0))) {
      try {
        const prepared = await prepareImage(file)
        added.push({ file: prepared, url: URL.createObjectURL(prepared), alt: '' })
      } catch (e) {
        errors.push(e.message)
      }
    }
    setPreparing(false)
    setImages((current) => [...current, ...added])
    setError([...new Set(errors)].join(' / '))
  }

  function removeImage(index) {
    URL.revokeObjectURL(images[index].url)
    setImages((current) => current.filter((_, i) => i !== index))
  }

  async function submit(event) {
    event?.preventDefault()
    if (!canSubmit) return
    const form = new FormData()
    form.append('text', text)
    for (const image of images) {
      form.append('images', image.file)
      form.append('alts', image.alt)
    }
    setSubmitting(true)
    try {
      const post = await api('/api/posts', { method: 'POST', form })
      images.forEach((image) => URL.revokeObjectURL(image.url))
      setText('')
      setImages([])
      setError('')
      window.dispatchEvent(new CustomEvent(POST_CREATED, { detail: post }))
      notify('投稿しました')
      onPosted?.(post)
    } catch (e) {
      // 入力した内容は消さずに残す
      notify(e.message, { error: true })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className={`composer${id === 'home' ? ' hide-sm' : ''}`} onSubmit={submit} noValidate>
      <Avatar user={user} />
      <div className="grow">
        <label className="sr-only" htmlFor={`text-${id}`}>
          投稿の本文
        </label>
        <textarea id={`text-${id}`} placeholder="いまどうしてる?" value={text}
          aria-describedby={`counter-${id}${error ? ` error-${id}` : ''}`}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) submit(e)
          }}
          onPaste={(e) => {
            const files = [...e.clipboardData.files].filter((f) => f.type.startsWith('image/'))
            if (files.length) {
              e.preventDefault()
              addFiles(files)
            }
          }} />
        {images.length > 0 && (
          <div className="thumbs">
            {images.map((image, i) => (
              <div className="thumb" key={image.url}>
                <div className="pic">
                  <img src={image.url} alt="" />
                  <button className="remove" type="button" aria-label={`画像 ${i + 1} を取り消す`} onClick={() => removeImage(i)}>
                    <Icon name="close" size={18} />
                  </button>
                </div>
                <label className="sr-only" htmlFor={`alt-${id}-${i}`}>
                  画像 {i + 1} の説明(代替テキスト)
                </label>
                <input type="text" id={`alt-${id}-${i}`} placeholder="画像の説明を追加" value={image.alt}
                  onChange={(e) => {
                    const alt = e.target.value
                    setImages((current) => current.map((x, j) => (j === i ? { ...x, alt } : x)))
                  }} />
              </div>
            ))}
          </div>
        )}
        {error && (
          <p className="field-error" id={`error-${id}`}>
            {error}
          </p>
        )}
        <div className="composer-foot">
          <input ref={fileInput} className="sr-only" type="file" tabIndex={-1} multiple
            accept="image/jpeg,image/png,image/webp,image/gif"
            onChange={(e) => {
              addFiles([...e.target.files])
              e.target.value = ''
            }} />
          <button className="icon-btn" type="button" onClick={() => fileInput.current.click()}
            aria-label={`画像を追加(最大10枚。いまの枚数 ${images.length}枚)`} disabled={images.length >= MAX_IMAGES}>
            <Icon name="image" />
          </button>
          {preparing && <span role="status">画像を準備しています…</span>}
          <span className="spacer" />
          <span id={`counter-${id}`} className={`counter${left < 0 ? ' over' : left < 20 ? ' warn' : ''}`}>
            {left < 0 ? `${-left} 文字オーバー` : `残り ${left} 文字`}
          </span>
          <button className="btn btn-primary" type="submit" disabled={!canSubmit}>
            {submitting ? '送信中…' : '投稿する'}
          </button>
        </div>
      </div>
    </form>
  )
}
