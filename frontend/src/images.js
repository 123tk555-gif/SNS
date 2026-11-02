// 投稿する画像の準備(NF-IMG-01, 02)。送る前に、ブラウザ標準の canvas で縮小し、WebP に変換して小さくする
// (追加の部品は使わない)

export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
export const MAX_BYTES = 5 * 1024 * 1024
export const MAX_IMAGES = 10
const MAX_EDGE = 2048
// WebP の画質(0〜1)。0.85 なら、見た目はほとんど変わらずに、JPEG・PNG より大きく小さくなる
const WEBP_QUALITY = 0.85
const TOO_LARGE = '5MB以下の画像を選んでください'

function loadImage(file) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file)
    const image = new Image()
    image.onload = () => {
      URL.revokeObjectURL(url)
      resolve(image)
    }
    image.onerror = () => {
      URL.revokeObjectURL(url)
      reject(new Error('画像を読み込めませんでした。別の画像を選んでください'))
    }
    image.src = url
  })
}

function toBlob(canvas, type, quality) {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality))
}

/** ファイル名の拡張子を付け替える(photo.png → photo.webp) */
function rename(name, extension) {
  const dot = name.lastIndexOf('.')
  return `${dot > 0 ? name.slice(0, dot) : name}.${extension}`
}

/**
 * 画像を送る形に整える。
 * - 長い辺が 2048px を超えるものは縮小する
 * - JPEG・PNG・WebP は、WebP(画質 0.85)に描き直す。大きさがおよそ 1/3〜1/10 になる。
 *   描き直すので、写真に入っている撮影場所(GPS)などの情報も消える
 * - GIF は、動きが消えてしまうので、そのまま送る
 * - WebP で書き出せないブラウザでは、元の形式のまま(大きいときだけ縮小して)送る
 * 形式が違う・小さくしても 5MB を超えるときは、Error(画面に出す文)を投げる
 */
export async function prepareImage(file) {
  if (!IMAGE_TYPES.includes(file.type)) throw new Error('JPEG・PNG・WebP・GIF の画像を選んでください')
  if (file.type === 'image/gif') {
    if (file.size > MAX_BYTES) throw new Error(TOO_LARGE)
    return file
  }
  const image = await loadImage(file)
  const scale = Math.min(1, MAX_EDGE / Math.max(image.naturalWidth, image.naturalHeight))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(image.naturalWidth * scale))
  canvas.height = Math.max(1, Math.round(image.naturalHeight * scale))
  canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height)

  let result
  const webp = await toBlob(canvas, 'image/webp', WEBP_QUALITY)
  if (webp?.type === 'image/webp') {
    result = new File([webp], rename(file.name, 'webp'), { type: 'image/webp' })
  } else if (scale < 1) {
    // WebP で書き出せない(PNG などで返ってくる)ブラウザでは、元の形式で縮小する
    const blob = await toBlob(canvas, file.type, 0.9)
    result = new File([blob], file.name, { type: blob.type })
  } else {
    result = file
  }
  if (result.size > MAX_BYTES) throw new Error(TOO_LARGE)
  return result
}
