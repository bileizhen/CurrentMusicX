# Android 网页链接

应用接收 HTTP/HTTPS 链接，冷启动和已运行时均可打开：

- 网易云 `music.163.com`、`y.music.163.com` 的歌曲、歌单、专辑、歌手、MV；支持 `/#/song?id=…`、`/song?id=…`、`/m/song?id=…` 等形式。
- `163cn.tv` 短链接：匿名请求，最多跟随 5 次 HTTP 重定向，逐跳检查域名；不执行网页脚本，不访问任意跳转目标。
- CurrentMusic 四个内置站点的 `/app/#/song/…`、`/app/#/ncmpl/…`、`/app/#/pl/…`、`/app/#/album/…`、`/app/#/artist/…`、`/app/#/mv/…`，以及首页。站点根目录的同类 hash 链接也受支持。

歌曲链接读取歌曲信息后播放并展开播放器；歌单、专辑、歌手和 MV 打开对应详情页。链接不会登录、修改服务器或自动加入房间；一起听期间不替换当前歌曲。未支持的路由显示提示。

## 网站验证

`assetlinks.json` 是网站部署文件，不是 APK 资源。将其中的关联**合并**到下列各站点的 `https://域名/.well-known/assetlinks.json`，保留其他合法条目：

- `music.20110208.xyz`
- `cm.bileizhen.top`
- `currentmusic.geekertao.com`
- `music.ctcehs.com`

要求 HTTPS 返回 200、`Content-Type: application/json`，不重定向。样例使用现有正式站点公布的 CurrentMusicX 发布签名指纹；部署前与实际发布 APK 的签名核对。保留 `common.get_login_creds` 以继续支持通行密钥，并添加 `common.handle_all_urls`。调试包及 `.verification` 测试包不自动获得正式域名验证。

仅修改 APK 无法完成网站关联。2026-11-09 检查时，主站和 geekertao 站点只有通行密钥关联，另外两个站点返回 404。此仓库中的配置不会自动部署到这些网站。

网易云域名由第三方控制，不能由本项目添加验证文件。Android 12 及以上进入应用「设置 → 默认打开链接」，在系统页面允许对应域名；具体入口随系统变化，网易云官方客户端可能已占用默认关联。

## 验收

发布签名 APK 安装后，先验证网站关联：

```sh
adb shell pm verify-app-links --re-verify com.bileizhen.currentmusic
adb shell pm get-app-links com.bileizhen.currentmusic
```

等待系统验证后，确认 CurrentMusic 域名显示 `verified`。网易云域名需用户允许，不能将其写成自动验证成功。

通过聊天或浏览器点击以下链接，分别检查应用关闭、已运行、连续点击不同链接、旋转屏幕和返回键；歌曲需要联网及当前音源支持：

- `https://music.163.com/song?id=347230`
- `https://music.163.com/#/playlist?id=3778678`
- `https://y.music.163.com/m/album?id=32311`
- `https://music.20110208.xyz/app/#/song/347230`
- `https://cm.bileizhen.top/app/#/ncmpl/3778678`

指定包名的 `adb shell am start -a android.intent.action.VIEW -d 'URL' -p com.bileizhen.currentmusic` 只能验证应用处理流程，不能证明系统默认分发或域名验证通过。真机验收需额外使用不指定包名的链接打开操作。
