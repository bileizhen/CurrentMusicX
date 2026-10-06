// CurrentMusic development team, displayed with the LeiFetch member cards.
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
            AboutMember("2536843865", "Hutao_felicity", "镜像站",
                githubUrl = "https://github.com/Geekertao"),
        )),
    )
}
