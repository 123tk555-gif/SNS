import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'

// お知らせ欄(画面仕様定義書 3.3)。成功は 5 秒で消え、エラーは「閉じる」を押すまで残す。
// 表示した文は読み上げソフトにも伝える(role="status")
const NoticeContext = createContext(() => {})

export function NoticeProvider({ children }) {
  const [notice, setNotice] = useState(null)
  const timer = useRef(null)

  const show = useCallback((message, { error = false } = {}) => {
    clearTimeout(timer.current)
    setNotice({ message, error })
    if (!error) timer.current = setTimeout(() => setNotice(null), 5000)
  }, [])

  useEffect(() => () => clearTimeout(timer.current), [])

  return (
    <NoticeContext.Provider value={show}>
      {children}
      <div className={`toast${notice?.error ? ' error' : ''}`} role="status" aria-live="polite" hidden={!notice}>
        {notice && (
          <>
            <span>{notice.error ? `エラー: ${notice.message}` : notice.message}</span>
            {notice.error && (
              <button type="button" onClick={() => setNotice(null)}>
                閉じる
              </button>
            )}
          </>
        )}
      </div>
    </NoticeContext.Provider>
  )
}

export function useNotice() {
  return useContext(NoticeContext)
}
