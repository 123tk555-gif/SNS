import { defineConfig } from 'vitest/config'

// 画面の単体テスト(Vitest)。ロジックの部分を、ブラウザなしで確かめる(画面を通した確認は e2e/ の操作テスト)
export default defineConfig({
  test: {
    include: ['src/**/*.test.{js,jsx}'],
    // 日時の表示のテストを、手元(日本時間)でも CI(世界標準時)でも同じ結果にする
    env: { TZ: 'Asia/Tokyo' },
  },
})
