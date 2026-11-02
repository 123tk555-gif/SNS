import { useNotice } from '../notice.jsx'
import { relationMessage, setRelation } from '../relations.js'
import Modal from './Modal.jsx'

/** D-04 ブロックの確認(F-BLOCK-02)。最初は「やめる」を選んだ状態にする。onBlocked(): ブロックしたあと */
export default function BlockDialog({ user, onClose, onBlocked }) {
  const notify = useNotice()

  async function block() {
    try {
      await setRelation(user, 'block', true)
      onClose()
      onBlocked?.()
      notify(relationMessage(user.handle, 'block', true))
    } catch (e) {
      onClose()
      notify(e.message, { error: true })
    }
  }

  return (
    <Modal title={`@${user.handle} さんをブロックしますか?`} onClose={onClose} initialFocus="block-cancel">
      <ul>
        <li>お互いの投稿が見えなくなります</li>
        <li>お互いのフォローが解除されます</li>
        <li>相手はあなたをフォロー・返信・いいねできなくなります</li>
      </ul>
      <div className="dialog-buttons">
        <button id="block-cancel" className="btn btn-outline" type="button" onClick={onClose}>
          やめる
        </button>
        <button className="btn btn-danger" type="button" onClick={block}>
          ブロックする
        </button>
      </div>
    </Modal>
  )
}
