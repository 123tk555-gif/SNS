package com.example.sns.hashtag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** ハッシュタグ(機能定義書 4 の hashtags テーブル)。名前は小文字・半角にそろえて保存する(Hashtags.normalize)。 */
@Entity
@Table(name = "hashtags")
public class Hashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    protected Hashtag() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
