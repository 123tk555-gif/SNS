/**
 * Leafline のロゴマーク: 1 枚の若葉と、その中の 3 本の線(葉脈であり、タイムラインに並ぶ投稿でもある)。
 * 葉は周りの文字の色(currentColor)、線はアクセントの色。緑の四角(.brand-mark)の上では、白い葉に緑の線が抜けて見える
 */
export default function Logo({ size = 24 }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" focusable="false">
      <path d="M3 21C3 10 9.5 3 21 3c0 11.5-7 18-18 18Z" fill="currentColor" />
      <path d="M12.5 8.5h3.5M9.5 12h5M7 15.5h4" stroke="var(--accent)" strokeWidth="1.7" strokeLinecap="round" />
    </svg>
  )
}
