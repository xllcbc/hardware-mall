package com.example.mystore.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 微信消息推送加解密测试
 * 向量取自微信官方文档「消息推送」示例
 */
class WxMsgCryptTest {

    private static final String TOKEN = "AAAAA";
    private static final String AES_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String APPID = "wxba5fad812f8e6fb9";

    @Test
    void checkSignature_getVerifyVector_shouldPass() {
        // 官方 GET 验证示例
        boolean ok = WxMsgCrypt.checkSignature(
                TOKEN, "1714036504", "1514711492",
                "f464b24fc39322e44b38aa78f5edd27bd1441696");
        assertThat(ok).isTrue();
    }

    @Test
    void checkSignature_wrongSignature_shouldFail() {
        assertThat(WxMsgCrypt.checkSignature(TOKEN, "1714036504", "1514711492", "deadbeef")).isFalse();
    }

    @Test
    void checkMsgSignature_secureModeVector_shouldPass() {
        // 官方安全模式示例: sha1(sort(token,timestamp,nonce,Encrypt))
        boolean ok = WxMsgCrypt.checkMsgSignature(
                TOKEN, "1714112445", "415670741",
                EncryptVector.SECURE_MODE_ENCRYPT,
                "046e02f8204d34f8ba5fa3b1db94908f3df2e9b3");
        assertThat(ok).isTrue();
    }

    @Test
    void decrypt_secureModeVector_shouldReturnPlainJson() {
        String plain = WxMsgCrypt.decrypt(AES_KEY, EncryptVector.SECURE_MODE_ENCRYPT, APPID);
        assertThat(plain).contains("\"Event\":\"debug_demo\"");
        assertThat(plain).contains("\"MsgType\":\"event\"");
    }

    @Test
    void decrypt_appidMismatch_shouldThrow() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> WxMsgCrypt.decrypt(AES_KEY, EncryptVector.SECURE_MODE_ENCRYPT, "wx_other"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("appid 不匹配");
    }

    /** 官方示例密文 */
    private static final class EncryptVector {
        static final String SECURE_MODE_ENCRYPT =
                "+qdx1OKCy+5JPCBFWw70tm0fJGb2Jmeia4FCB7kao+/Q5c/ohsOzQHi8khUOb05JCpj0JB4RvQMkUyus8TPxLKJ"
                        + "GQqcvZqzDpVzazhZv6JsXUnnR8XGT740XgXZUXQ7vJVnAG+tE8NUd4yFyjPy7GgiaviNrlCTj+l5kdfMuFUPpRSr"
                        + "fMZuMcp3Fn2Pede2IuQrKEYwKSqFIZoNqJ4M8EajAsjLY2km32IIjdf8YL/P50F7mStwntrA2cPDrM1kb6mOcfBg"
                        + "RtWygb3VIYnSeOBrebufAlr7F9mFUPAJGj04=";
    }
}
