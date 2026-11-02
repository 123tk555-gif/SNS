import { useEffect, useRef } from 'react'

/**
 * ダイアログ(画面仕様定義書 3.5)。ブラウザ標準の dialog を使う(taskboard と同じ)。
 * Esc キーでも閉じられ、閉じると、開いたときに押していたボタンにキーボードの位置を戻す。
 * initialFocus: 開いたときに選ぶ要素の id(取り消せない操作の確認では「やめる」にする)
 */
export default function Modal({ title, onClose, initialFocus, className = '', children }) {
  const dialog = useRef(null)

  useEffect(() => {
    const opener = document.activeElement
    const element = dialog.current
    element.showModal()
    if (initialFocus) document.getElementById(initialFocus)?.focus()
    return () => {
      if (element.open) element.close()
      opener?.focus?.()
    }
  }, [initialFocus])

  return (
    <dialog ref={dialog} className={className} aria-labelledby="dialog-title"
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}>
      <h2 id="dialog-title">{title}</h2>
      {children}
    </dialog>
  )
}
