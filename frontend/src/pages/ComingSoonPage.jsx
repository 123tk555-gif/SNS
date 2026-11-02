import Layout from '../components/Layout.jsx'

/** まだ作っていない画面。どのマイルストーンで作るかを表示する */
export default function ComingSoonPage({ title, milestone }) {
  return (
    <Layout title={title}>
      <div className="empty">
        <p>この画面は {milestone} で作ります。</p>
      </div>
    </Layout>
  )
}
