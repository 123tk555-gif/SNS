package com.example.sns.post;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 投稿の画像(機能定義書 4 の post_images テーブル)。ファイルは uploads/ に保存し、ここには保存場所だけを持つ。 */
@Entity
@Table(name = "post_images")
public class PostImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id")
    private Post post;

    @Column(nullable = false)
    private String path;

    /** 代替テキスト(1000 文字まで。空文字も可) */
    @Column(nullable = false, length = 4000)
    private String alt;

    /** 画像の大きさ(画面で、読み込み前に場所を空けておくため) */
    @Column(nullable = false)
    private int width;

    @Column(nullable = false)
    private int height;

    /** 表示順(0〜9) */
    @Column(nullable = false)
    private short position;

    protected PostImage() {
    }

    PostImage(Post post, String path, String alt, int width, int height, int position) {
        this.post = post;
        this.path = path;
        this.alt = alt;
        this.width = width;
        this.height = height;
        this.position = (short) position;
    }

    String getPath() {
        return path;
    }
}
