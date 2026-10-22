package com.sampwrap.core;

import java.io.IOException;

/**
 * IOException yang membawa kode status HTTP.
 *
 * Kenapa perlu: sebagian implementasi HttpURLConnection (OpenJDK) melempar
 * IOException untuk balasan 4xx, dan pada kasus redirect (Drive mengarahkan
 * ke URL ber-uuid) kode 416 baru muncul sebagai "chained exception" saat body
 * dibaca — getResponseCode() bisa mengembalikan kode lain. Android sendiri
 * mengembalikan kode dengan normal. Supaya dua-duanya tertangani, error
 * semacam itu dibawa sebagai exception ini dan pemanggil memperlakukannya
 * seperti kode status biasa.
 */
public class HttpStatusException extends IOException {

    public final int code;

    public HttpStatusException(int code, String message) {
        super(message);
        this.code = code;
    }

    public HttpStatusException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
