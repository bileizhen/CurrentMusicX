# Android 原生通行密钥：后端适配方案

本方案对应 CurrentMusicX 的原生登录入口。客户端使用 Android Credential Manager，**不打开网页，不伪造 HTTPS 网页 Origin**。后端部署以下改动之前，生产环境的原生通行密钥登录仍不可用；密码、邮箱验证码、密保和验证器登录不受此限制。

## 1. 当前差异与本次客户端范围

核对依据：后端 `backrooms-yrc/CurrentMusic` 的 `b51e197b13a83cd418e915bd660aeb2a2ff07a34`。

- [`backend/cm_passkeys.py`](https://github.com/backrooms-yrc/CurrentMusic/blob/b51e197b13a83cd418e915bd660aeb2a2ff07a34/backend/cm_passkeys.py)：`configured_origins()` 只允许 HTTPS；`origin_check()` 用 URL hostname 推导 RP ID；`begin()` 与 `finish()` 都依赖它。
- [`backend/cm_server.py`](https://github.com/backrooms-yrc/CurrentMusic/blob/b51e197b13a83cd418e915bd660aeb2a2ff07a34/backend/cm_server.py)：通行密钥路由从 HTTP `Origin` 头读取来源，再交给 `cm_passkeys`。
- 主站 `/.well-known/assetlinks.json` 本次检查返回 404；不带网页 Origin 调用 options 返回 403，并要求使用 HTTPS 网页入口。

客户端本次实现：**读取已有通行密钥并登录**。不包含原生创建／撤销通行密钥；已有凭据必须属于下述相同 RP ID。其他 CDN 域名下创建的凭据不会自动迁移。

## 2. 固定身份、域名与证书

| 项目 | 值 |
| --- | --- |
| 正式包名 | `com.bileizhen.currentmusic` |
| 内置线路的认证 API | `https://music.20110208.xyz/cm/` |
| RP ID | `music.20110208.xyz` |
| 正式签名 SHA-256 | `FC:3E:63:07:9B:8E:69:AE:A7:AB:73:48:28:46:DA:09:39:D6:CD:12:DA:F0:AF:FF:AA:9A:2E:93:FB:FC:CF:35` |
| Android Origin | `android:apk-key-hash:_D5jB5uOaa6nq3NIKEbaCTnWzRLa8K__qpouk_v8zzU` |

指纹来自本地 Gradle 正式签名配置的 `signingReport`，为公开证书信息。上线前必须再用实际发布 APK 的 `apksigner verify --print-certs` 核对；若实际分发签名不同，应同时更新 DAL 与后端来源白名单。

Origin 后缀是 **证书 DER 字节的 SHA-256 摘要，经 Base64URL 无填充编码**，不是十六进制文本的 Base64。客户端读取当前 APK 签名计算它，Credential Manager 也会以实际签名生成 `clientDataJSON.origin`。

内置主站／CDN 的通行密钥请求始终固定到主站，其他音乐请求仍按现有线路选择。自定义服务器保持自己的 HTTPS 地址及 hostname 作为 RP ID，需要自行配置关联和后端来源映射。普通 HTTP 自定义服务器不支持此入口。

## 3. 发布 Digital Asset Links

在 **域名根路径** `https://music.20110208.xyz/.well-known/assetlinks.json` 发布以下内容，不要放在 `/cm/` 下：

```json
[
  {
    "relation": ["delegate_permission/common.get_login_creds"],
    "target": {
      "namespace": "android_app",
      "package_name": "com.bileizhen.currentmusic",
      "sha256_cert_fingerprints": [
        "FC:3E:63:07:9B:8E:69:AE:A7:AB:73:48:28:46:DA:09:39:D6:CD:12:DA:F0:AF:FF:AA:9A:2E:93:FB:FC:CF:35"
      ]
    }
  }
]
```

要求 HTTPS 证书可信、直接返回 200、`Content-Type: application/json`、无需登录且不经过验证码／登录跳转。正式域名不要加入通用 debug 签名；隔离测试包 `com.bileizhen.currentmusic.verification` 应使用测试域名和单独关联。

参考：[Android Credential Manager 前置要求](https://developer.android.com/identity/credential-manager/prerequisites)。

## 4. 后端改动位置

### `backend/cm_passkeys.py`

保留现有网页来源配置，新增独立的 **Android Origin → RP ID 精确映射**。例如配置 `CM_PASSKEY_ANDROID_ORIGINS` 为 JSON 字典；不要把 Android 字符串混入仅允许 HTTPS 的 `CM_PASSKEY_ORIGINS`。

```python
# 示例：实际应从经过严格校验的服务端配置读取。
ANDROID_ORIGINS = {
    "android:apk-key-hash:_D5jB5uOaa6nq3NIKEbaCTnWzRLa8K__qpouk_v8zzU":
        "music.20110208.xyz",
}

def origin_check(origin):
    if origin in ANDROID_ORIGINS:
        return ANDROID_ORIGINS[origin]
    if origin not in ORIGINS:
        raise Error("此应用或网页尚未获准使用通行密钥", 403)
    return urlparse(origin).hostname
```

这是适配思路，不能用 `startswith('android:')` 放开全部应用，也不能从请求体接受任意 RP ID。配置加载时验证哈希是 32 字节摘要的无填充 Base64URL、RP ID 是明确允许的域名，不接受通配符。未配置 Android 映射时保持拒绝。

`begin()` 按映射返回正确的 `publicKey.rpId`。`finish()` 必须同时校验：

1. 本次请求来源属于白名单，且与创建挑战时保存的来源一致。
2. `clientDataJSON.origin` 精确等于挑战绑定的 Android Origin；使用 WebAuthn 库的 `expected_origin` 校验。
3. `expected_challenge`、`expected_rp_id`、签名、credential ID、用户句柄、账号状态和签名计数符合现有规则。
4. 保持 `require_user_verification=True`，不降级为仅触摸确认。
5. 保持 5 分钟有效期、单次消费、事务处理和注册时的账号／会话绑定。

HTTP `Origin` 只用于选择和绑定流程，**不是身份凭据**；可信结论来自对绑定数据和签名的完整验证。继续把实际 Android Origin 存进挑战的 `origin` 列，无需把它转换为网页来源。建议额外保存挑战创建时的 RP ID；如果采用动态映射，则变更配置时清理旧挑战，不能让同一挑战在新旧 RP 间漂移。

上述映射同时适用于后端现有注册与验证函数；本次客户端只调用 login 接口。无需修改账户登录 token 格式或把 2FA／密保加到已经完成用户验证的 passkey 登录后。

### `backend/cm_server.py` 与反向代理

沿用现有 `/auth/passkeys/login/options` 与 `/auth/passkeys/login/verify`。确认反向代理转发 `Origin: android:apk-key-hash:…`，没有把它替换为主站 HTTPS Origin，也没有提前按网页 CORS 白名单拒绝原生请求。只允许上述精确来源，不放宽网页跨域策略。

若需要统一返回新的稳定错误码，可增加 `errorCode: "native_passkey_not_configured"`，但仍须保留可读的 `error` 字段。本次客户端兼容目前后端的 403 文本；升级错误码后应同步补充客户端映射。

## 5. 与客户端一致的请求契约

### 获取认证选项

```http
POST /cm/auth/passkeys/login/options
Content-Type: application/json
Origin: android:apk-key-hash:_D5jB5uOaa6nq3NIKEbaCTnWzRLa8K__qpouk_v8zzU

{"platform":"app"}
```

响应保持现有结构，可额外返回 `expiresIn`：

```json
{
  "challengeToken": "<不透明的一次性挑战凭据>",
  "expiresIn": 300,
  "publicKey": {
    "challenge": "<Base64URL 随机挑战>",
    "rpId": "music.20110208.xyz",
    "userVerification": "required",
    "allowCredentials": []
  }
}
```

客户端仅在 RP ID 与固定认证域名一致、要求用户验证时调用系统 Credential Manager。未返回 `expiresIn` 时按现有协议使用 300 秒；返回值最多允许 300 秒。

### 提交凭据

```http
POST /cm/auth/passkeys/login/verify
Content-Type: application/json
Origin: android:apk-key-hash:_D5jB5uOaa6nq3NIKEbaCTnWzRLa8K__qpouk_v8zzU

{
  "challengeToken": "<上一步返回值>",
  "credential": {
    "id": "<credential ID>",
    "rawId": "<Base64URL credential ID>",
    "type": "public-key",
    "response": {
      "clientDataJSON": "<Base64URL clientDataJSON>",
      "authenticatorData": "<Base64URL authenticatorData>",
      "signature": "<Base64URL signature>",
      "userHandle": "<Base64URL userHandle>"
    }
  },
  "device": "<现有设备描述>",
  "platform": "app"
}
```

验签成功仍返回 `{ "token": "…", "user": { "id": 123, ... } }`。两个接口均为匿名登录请求，不携带已有账号的 Bearer token；客户端仅在验签响应合法后保存新会话。取消、超时、账号／服务器切换或认证失败均不得保存会话。

参考：[Android 原生通行密钥登录](https://developer.android.com/identity/passkeys/sign-in-with-passkeys)。

## 6. 联调与上线验收

先为后端补测试，再部署测试环境：

- 正式白名单 Android Origin 可创建挑战，任意其他签名、任意 Android 前缀、缺少 Origin 均失败。
- 合法 Android 签名断言成功；伪造 HTTP Origin 但 `clientDataJSON.origin` 不符、错误 RP ID、错误 challenge、无 UV、错误签名、其他用户句柄均失败。
- 挑战过期、重复提交、网页挑战与 Android 互换、其他凭据／账号、禁用账号、计数器回退均按原规则拒绝。
- 原有四个 HTTPS 网页来源的绑定／登录仍正常；新来源支持不扩大网页许可。
- 反向代理保留 Android Origin，DAL 以正确内容类型直接返回 200。

再使用**正式签名 APK**和可信设备凭据提供程序进行端到端验收：

1. 在主站 RP 下已有可发现通行密钥，系统能展示并完成登录。
2. 主站与各内置 CDN 选择下均使用同一 RP；自定义服务器不串到主站账号。
3. 取消系统弹窗、没有凭据、设备不支持、网络中断、后台恢复、切换账号不会误登录或泄漏凭据。
4. 原密码登录、邮箱验证码及其密保／验证器第二步仍可使用。

客户端模拟服务端测试可以验证协议、状态隔离和取消处理，但不能替代上述真实 Credential Manager、DAL 和后端验签验收。仅添加入口不代表线上原生通行密钥登录已经可用。
