package com.example.sns.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 画面の URL(/users/abc など)を直接開いたり再読み込みしたりしたときに、React の index.html を返す。
 * どの画面を表示するか(存在しない画面なら「ページが見つかりません」)は、React(react-router)が URL を見て決める。
 * 次の URL は対象外: /api・/media(サーバーの API と画像。存在しなければ JSON の 404)、/oauth2(Google ログイン)、
 * /error(Spring Boot のエラー処理)、/assets と「.」を含む URL(ビルドした JS・CSS などのファイル)。
 * npm run build のあと(Spring Boot が画面も配信するとき)に使う。開発中は Vite が同じことをする。
 */
@Controller
class SpaForwardController {

    private static final String FIRST_SEGMENT = "{first:(?!api$|media$|oauth2$|error$|assets$)[^.]*}";

    @GetMapping({"/", "/" + FIRST_SEGMENT, "/" + FIRST_SEGMENT + "/**"})
    String index() {
        return "forward:/index.html";
    }
}
