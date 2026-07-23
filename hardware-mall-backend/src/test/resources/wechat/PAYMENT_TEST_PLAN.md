# 微信支付测试添加上手指南

## 背景
当前 `application-test.yml` 设置了 `wechat.pay.mch-id: "false"`，
使 `WechatPayConfig` 和 `PayServiceImpl` 在测试环境不加载。
原理：Spring Boot 的 `@ConditionalOnProperty` 默认行为——属性值为 `"false"` 时条件不命中。
这在没有真实微信商户号的 CI 环境里是必须的。

## 将来启用支付测试时，按顺序做：

### 1. 生成测试密钥对
```bash
openssl genrsa -out src/test/resources/wechat/test_key.pem 2048
openssl rsa -in src/test/resources/wechat/test_key.pem -pubout -out src/test/resources/wechat/test_key.pub
```

⚠️ 测试密钥不要用生产密钥，不要提交 .pem 进 git（加到 .gitignore）

### 2. 修改 application-test.yml
把 `mch-id: "false"` 替换为：
```yaml
wechat:
  appid: wx-test-appid-for-ci
  secret: test-secret-for-ci
  pay:
    mch-id: "1900000109"              # 微信测试商户号（或用假的）
    api-v3-key: ${WECHAT_API_V3_KEY:test-api-v3-key-32bytes-minimum}
    private-key: ${WECHAT_PRIVATE_KEY:}   # CI 通过 GitHub Secret 注入 base64
    public-key: ${WECHAT_PUBLIC_KEY:}
    public-key-id: ${WECHAT_PUBLIC_KEY_ID:test-pub-key-id}
    mch-serial-no: ${WECHAT_MCH_SERIAL_NO:test-serial-no}
    notify-url: ${WECHAT_PAY_NOTIFY_URL:http://localhost:8080/api/pay/notify/test}
```

### 3. 写测试类
- `PayServiceImplTest.java` — Mockito 单元测试，mock WeChat SDK 的 JsapiServiceExtension
- `WechatPayConfigTest.java` — 验证 RSA 配置正确加载
- 如果 `.pem` 进了 gitignore，在 CI 里 key 通过 GitHub Secrets 传入

### 4. .gitignore 加一行
```
hardware-mall-backend/src/test/resources/wechat/*.pem
```
