import { useState } from 'react'

/**
 * 名前付きの入力欄。エラーは欄の下に表示し、欄と結びつけて読み上げソフトにも伝える(画面仕様定義書 3.6)。
 * type="password" のときは「表示」ボタンで文字を見せる・隠すを切り替えられる。
 */
export default function Field({ id, label, hint, error, type = 'text', inputRef, ...inputProps }) {
  const [visible, setVisible] = useState(false)
  const describedBy = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ') || undefined
  const input = (
    <input
      id={id}
      ref={inputRef}
      type={type === 'password' && visible ? 'text' : type}
      aria-invalid={error ? 'true' : undefined}
      aria-describedby={describedBy}
      {...inputProps}
    />
  )

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {type === 'password' ? (
        <div className="pw-row">
          {input}
          <button className="btn btn-outline" type="button" aria-pressed={visible} onClick={() => setVisible((v) => !v)}>
            {visible ? '隠す' : '表示'}
          </button>
        </div>
      ) : (
        input
      )}
      {hint && (
        <p className="hint" id={`${id}-hint`}>
          {hint}
        </p>
      )}
      {error && (
        <p className="field-error" id={`${id}-error`}>
          {error}
        </p>
      )}
    </div>
  )
}
