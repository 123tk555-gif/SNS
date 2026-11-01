import { Link } from 'react-router'
import AuthLayout from '../components/AuthLayout.jsx'

/** S-99 ページが見つかりません */
export default function NotFoundPage() {
  return (
    <AuthLayout title="ページが見つかりません">
      <p>お探しのページは削除されたか、URL が間違っている可能性があります。</p>
      <Link className="btn btn-primary btn-block" to="/">
        ホームへ戻る
      </Link>
    </AuthLayout>
  )
}
