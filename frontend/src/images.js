// 投稿する画像の準備(NF-IMG-01, 02)。送る前にブラウザ標準の canvas で縮小する(追加の部品は使わない)

export const IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']
export const MAX_BYTES = 5 * 1024 * 1024
export const MAX_IMAGES = 10
const MAX_EDGE = 2048

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

/**
 * 長い辺が 2048px を超える画像を縮小する。GIF は動きが消えてしまうので縮小しない。
 * 形式が違う・縮小しても 5MB を超えるときは、Error(画面に出す文)を投げる
 */
export async function prepareImage(file) {
  if (!IMAGE_TYPES.includes(file.type)) throw new Error('JPEG・PNG・WebP・GIF の画像を選んでください')
  if (file.type === 'image/gif') {
    if (file.size > MAX_BYTES) throw new Error('5MB以下の画像を選んでください')
    return file
  }
  const image = await loadImage(file)
  const scale = Math.min(1, MAX_EDGE / Math.max(image.naturalWidth, image.naturalHeight))
  let result = file
  if (scale < 1) {
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(image.naturalWidth * scale)
    canvas.height = Math.round(image.naturalHeight * scale)
    canvas.getContext('2d').drawImage(image, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise((resolve) => canvas.toBlob(resolve, file.type, 0.9))
    result = new File([blob], file.name, { type: blob.type })
  }
  if (result.size > MAX_BYTES) throw new Error('5MB以下の画像を選んでください')
  return result
}
