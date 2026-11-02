package com.example.sns.post;

/** 画面に返す画像の情報(どの投稿の画像か、も含む)。PostImageRepository.findRows の結果 */
record PostImageRow(long postId, String path, String alt, int width, int height) {
}
