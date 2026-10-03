from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / 'app/src'
for p in src.rglob('*'):
    if p.is_file() and p.suffix in {'.kt', '.xml'}:
        s = p.read_text(encoding='utf-8')
        for a, b in [('LeiTemplateApp', 'CurrentMusicApp'), ('TemplateIcons', 'MusicIcons'), ('TemplateDialog', 'MusicDialog'), ('TemplateCapabilitiesTest', 'NativeCapabilitiesTest'), ('Theme.LeiTemplate', 'Theme.CurrentMusic'), ('template:', 'currentmusic:')]:
            s = s.replace(a, b)
        p.write_text(s, encoding='utf-8')
        if 'Template' in p.name:
            p.rename(p.with_name(p.name.replace('LeiTemplateApp', 'CurrentMusicApp').replace('TemplateIcons','MusicIcons').replace('TemplateDialog','MusicDialog').replace('TemplateCapabilitiesTest','NativeCapabilitiesTest')))
p = src / 'main/java/io/github/currencortex/music/core/config/AppMetadata.kt'
s = p.read_text(encoding='utf-8').replace('MIUIX + Jetpack Compose reusable Android application shell', '原生 Android 音乐客户端').replace('const val AUTHOR = "bileizhen"', 'const val AUTHOR = "CurrentMusic"').replace('const val GITHUB_OWNER = "bileizhen"', 'const val GITHUB_OWNER = "backrooms-yrc"')
p.write_text(s, encoding='utf-8')
p = src / 'main/java/io/github/currencortex/music/core/config/AboutCredits.kt'
s = p.read_text(encoding='utf-8')
s = s[:s.index('object AboutCredits')] + 'object AboutCredits {\n    val sections = emptyList<AboutSection>()\n}\n'
p.write_text(s, encoding='utf-8')
p = src / 'main/java/io/github/currencortex/music/feature/about/AboutScreen.kt'
p.write_text(p.read_text(encoding='utf-8').replace('AboutLink("LeiFetch", "https://github.com/bileizhen/LeiFetch")', 'AboutLink("原项目", "https://github.com/backrooms-yrc/CurrentMusic/tree/main")'), encoding='utf-8')
p = root / '.github/workflows/android.yml'
s = p.read_text().replace('branches: [ main ]', 'branches: [ app ]').replace('  pull_request:\n', '  pull_request:\n    branches: [ app ]\n')
p.write_text(s, encoding='utf-8')
p = root / 'app/build.gradle.kts'
p.write_text(p.read_text().replace('applicationIdSuffix = ".debug"', '').replace('"UPDATE_DIALOG_PREVIEW", "true"', '"UPDATE_DIALOG_PREVIEW", "false"'), encoding='utf-8')
p = src / 'main/java/io/github/currencortex/music/feature/home/HomeScreen.kt'
s = p.read_text(encoding='utf-8').replace('MIUIX + Compose 的可复用应用骨架', '音乐，从这里开始').replace('开始开发', '欢迎使用 CurrentMusic').replace('把业务代码放进 feature / data / core，UI 组件继续放在 ui/component。', '使用搜索寻找歌曲，登录你的 CurrentMusic 账户。')
p.write_text(s, encoding='utf-8')
mit = subprocess.check_output(['git', 'show', 'main:LICENSE'], cwd=root).decode('utf-8')
p = root / 'THIRD_PARTY_NOTICES.md'
p.write_text(p.read_text(encoding='utf-8') + '\n## CurrentMusic legacy behavior and API contracts\n\n' + mit, encoding='utf-8')
(src / 'main/assets/legal/NOTICES.md').write_text(p.read_text(encoding='utf-8'), encoding='utf-8')
(root / 'local.properties').write_text('sdk.dir=C:/Users/bileizhen/AppData/Local/Android/Sdk\n', encoding='utf-8')
