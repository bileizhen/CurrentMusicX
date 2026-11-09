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
            AboutMember("3140014249", "bileizhen", "开发 · 设计 · 维护",
                githubUrl = "https://github.com/bileizhen"),
            AboutMember("1945826346", "Rcst20", "原项目开发",
                githubUrl = "https://github.com/backrooms-yrc"),
            AboutMember("2468872022", "LinYe2804", "歌词体验 · 界面性能 · 横屏适配",
                detail = "优化迷你播放器歌词更新与过渡动画，修复歌词闪烁问题。\n" +
                    "新增 Apple Music 风格逐字歌词，优化歌词渲染流畅度。\n" +
                    "优化界面重组、播放队列序列化、歌单与搜索列表加载，以及页面模糊过渡和液态玻璃绘制，减少主线程与渲染开销。\n" +
                    "改善沉浸式封面透明渐变与横竖屏衔接，修复宽屏歌单白边和横屏迷你播放器律动光效，统一侧栏交互。\n" +
                    "修复从搜索进入 CD 唱片架时的渲染、排序筛选与旋转恢复问题，完善横屏布局和安全区适配。\n" +
                    "固定发现页顶部切页控件，避免与内容滚动发生冲突。",
                githubUrl = "https://github.com/LinYe2804"),
            AboutMember("442259851", "KelierAndes", "歌词交互 · 歌词搜索优化",
                detail = "修复歌词拖动时的高亮跟随与回弹跳变，改善快速拖动和返回当前演唱行的连续滑移体验。\n" +
                    "为歌词搜索增加命中句及前后文展示、匹配词高亮与缺失上下文补齐，并优化搜索结果布局。",
                githubUrl = "https://github.com/KelierAndes"),
            AboutMember("2711058826", "十三", "开发",
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
