import { useEffect, useRef } from 'react'

/** ログイン・登録などのカード型の画面。画面が変わったら見出しにキーボードの位置を移し、タブの題名を変える */
export default function AuthLayout({ title, notice, children }) {
  const heading = useRef(null)

  useEffect(() => {
    document.title = `${title} / SNS`
    heading.current?.focus()
  }, [title])

  return (
    <main id="main" className="auth">
      <div className="auth-card">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">@</span>
          SNS
        </div>
        <h1 tabIndex={-1} ref={heading}>
          {title}
        </h1>
        {notice && (
          <p className="field-ok" role="status">
            {notice}
          </p>
        )}
        {children}
      </div>
    </main>
  )
}
