// CurrentMusic contributors, displayed with the LeiFetch member cards.
package io.github.currencortex.music.core.config

data class AboutMember(
    val qq: String,
    val name: String,
    val role: String,
    val detail: String? = null,
    val githubUrl: String? = null,
)

data class AboutSection(val title: String, val members: List<AboutMember>)

object AboutCredits {
    val sections = listOf(
        AboutSection("开发组", listOf(
            AboutMember("3140014249", "bileizhen", "总开发 · 设计 · 维护",
                detail = "CurrentMusicX 总开发，负责项目整体开发、架构与界面设计。\n" +
                    "统筹功能迭代与贡献整合，持续维护应用体验、版本发布和项目协作。",
                githubUrl = "https://github.com/bileizhen"),
            AboutMember("1945826346", "Rcst20", "原项目开发 · 音源与播放适配",
                detail = "开发 CurrentMusic 原项目，并参与 CurrentMusicX 的音源与播放适配。\n" +
                    "为自动最高音质增加可播放杜比音源的优先选择与失败回退，修复网易云听歌上报的设备名称。\n" +
                    "修复桌面灵动岛歌词在文字未溢出时仍左右漂移的问题。\n" +
                    "补充中英双语实现原理图，说明音源、歌词与前后端请求链路。",
                githubUrl = "https://github.com/backrooms-yrc"),
            AboutMember("2468872022", "LinYe2804", "歌词体验 · 界面性能 · 横屏适配",
                detail = "优化迷你播放器歌词更新与过渡动画，修复歌词闪烁问题。\n" +
                    "新增 Apple Music 风格逐字歌词，支持间距、动画速度、辉光与模糊强度调节，以及翻译、罗马音和注音显示，优化歌词渲染流畅度。\n" +
                    "优化界面重组、播放队列序列化、歌单与搜索列表加载，以及页面模糊过渡和液态玻璃绘制，减少主线程与渲染开销。\n" +
                    "改善沉浸式封面透明渐变与横竖屏衔接，修复宽屏歌单白边和横屏迷你播放器律动光效，统一侧栏交互。\n" +
                    "修复从搜索进入 CD 唱片架时的渲染、排序筛选与旋转恢复问题，完善横屏布局和安全区适配。\n" +
                    "固定发现页顶部切页控件，避免与内容滚动发生冲突。",
                githubUrl = "https://github.com/LinYe2804"),
            AboutMember("442259851", "KelierAndes", "歌词交互 · 歌词搜索优化",
                detail = "修复歌词拖动时的高亮跟随与回弹跳变，改善快速拖动和返回当前演唱行的连续滑移体验。\n" +
                    "完善歌词浏览与播放跟随之间的切换：浏览时隐藏翻译和罗马音，返回当前歌词后恢复显示，回弹途中可重新拖动接管。\n" +
                    "为歌词搜索增加命中句及前后文展示、匹配词高亮与缺失上下文补齐，并优化搜索结果布局。",
                githubUrl = "https://github.com/KelierAndes"),
            AboutMember("2711058826", "十三", "歌词显示 · 全面屏适配",
                detail = "修复播放页封面歌词预览因固定行高导致的底部裁切，让行高与预览区域随字号和系统字体缩放调整。\n" +
                    "适配全面屏手势导航，让页面内容延伸至底部手势条区域，并为聊天输入区保留导航栏安全间距。",
                githubUrl = "https://github.com/WhiteXero"),
        )),
        AboutSection("CDN 组", listOf(
            AboutMember("2536843865", "Hutao_felicity", "CDN · currentmusic.geekertao.com",
                githubUrl = "https://github.com/Geekertao"),
            AboutMember("3140014249", "bileizhen", "CDN · cm.bileizhen.top",
                githubUrl = "https://github.com/bileizhen"),
            AboutMember("1462302892", "Var username:String?=null", "CDN · music.ctcehs.com"),
        )),
    )
}
