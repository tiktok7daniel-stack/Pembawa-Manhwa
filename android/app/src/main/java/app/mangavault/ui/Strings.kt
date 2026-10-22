package app.mangavault.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

val LocalLang = compositionLocalOf { "en" }

@Composable
fun t(key: String): String = Strings.get(LocalLang.current, key)

object Strings {
    fun get(lang: String, key: String): String = (if (lang == "ar") ar else en)[key] ?: en[key] ?: key

    private val en = mapOf(
        "badge" to "MANGA DOWNLOADER · CBZ", "h1a" to "Download Manga", "h1b" to "in Seconds",
        "lead" to "Paste a series link, choose your chapters, and get one clean CBZ file for smooth, ad-free offline reading.",
        "ph_url" to "Paste your manga or manhwa URL", "paste" to "Paste", "parse" to "Parse & Download", "parsing" to "Reading the page…",
        "browser" to "Browser mode", "browser_d" to "slower — for sites that need JavaScript",
        "disclaimer" to "MangaVault does not host any content. You are solely responsible for downloading only from sources that permit it.",
        "promo_t" to "FREE", "promo_d" to "No ads. No limits. Everything runs on your phone — no server needed.",
        "how" to "HOW IT WORKS", "swipe" to "SWIPE →",
        "s1_t" to "PASTE", "s1_d" to "Copy the series page link and paste it above. Or share a link to this app.",
        "s2_t" to "PICK", "s2_d" to "Choose every chapter, a range, or just the ones you like.",
        "s3_t" to "READ", "s3_d" to "Get a CBZ saved in Downloads/MangaVault and open it in any comic reader.",
        "nav_home" to "Home", "nav_downloads" to "Downloads", "nav_settings" to "Settings",
        "chapters" to "chapters", "search_ch" to "Search chapters…", "range" to "Select range", "all" to "Select all", "none" to "Clear",
        "fmt_single" to "One CBZ file", "fmt_single_d" to "All chapters merged into a single file",
        "fmt_zip" to "Folder of CBZs", "fmt_zip_d" to "One CBZ per chapter inside a folder named after the series",
        "download" to "Download", "pick_one" to "Select at least one chapter.", "chapter" to "Chapter", "back" to "Back",
        "st_running" to "Downloading…", "st_done" to "Done! Saved to your Downloads.", "st_error" to "Download failed", "st_cancelled" to "Cancelled",
        "s_queued" to "Waiting", "s_active" to "Downloading", "s_done" to "Done", "s_partial" to "Missing pages", "s_failed" to "Failed",
        "cancel" to "Cancel", "open_dl" to "Open Downloads", "saved_in" to "Saved in",
        "busy" to "A download is already running.",
        "err_BAD_URL" to "That doesn't look like a valid link.",
        "err_NO_CHAPTERS" to "No chapters found. Use the series page (not a chapter), try Browser mode, or the site may require a login/CAPTCHA.",
        "err_NO_PAGES" to "No page images found for this chapter.",
        "err_NETWORK" to "No internet connection or the site is unreachable.",
        "err_HTTP" to "The site answered with an error",
        "err_BROWSER" to "Browser mode failed to load the page.",
        "hist_empty" to "Nothing downloaded yet.", "hist_clear" to "Clear list", "files" to "file(s)",
        "set_lang" to "Language", "set_theme" to "Theme", "theme_dark" to "Dark", "theme_light" to "Light", "theme_system" to "System",
        "set_browser" to "Always use Browser mode", "set_cache" to "Clear cache", "cache_done" to "Cache cleared",
        "about" to "MangaVault saves manga as CBZ files. Download only from sources that permit it, and support creators by buying official releases.",
    )

    private val ar = mapOf(
        "badge" to "محمّل المانجا · CBZ", "h1a" to "حمّل المانجا", "h1b" to "في ثوانٍ",
        "lead" to "الصق رابط المانجا، اختر الفصول، واحصل على ملف CBZ واحد نظيف للقراءة بدون إنترنت وبدون إعلانات.",
        "ph_url" to "الصق رابط المانجا أو المانهوا", "paste" to "لصق", "parse" to "تحليل وتحميل", "parsing" to "جاري قراءة الصفحة…",
        "browser" to "وضع المتصفح", "browser_d" to "أبطأ — للمواقع التي تحتاج JavaScript",
        "disclaimer" to "MangaVault لا يستضيف أي محتوى. أنت المسؤول وحدك عن التحميل من المصادر التي تسمح بذلك فقط.",
        "promo_t" to "مجاني", "promo_d" to "بدون إعلانات. بدون حدود. كل شيء يعمل على هاتفك — بدون أي خادم.",
        "how" to "كيف يعمل", "swipe" to "← اسحب",
        "s1_t" to "الصق", "s1_d" to "انسخ رابط صفحة المانجا والصقه بالأعلى. أو شارك الرابط مع التطبيق.",
        "s2_t" to "اختر", "s2_d" to "اختر كل الفصول أو نطاقاً أو الفصول التي تريدها فقط.",
        "s3_t" to "اقرأ", "s3_d" to "احصل على ملف CBZ محفوظ في Downloads/MangaVault وافتحه في أي قارئ كوميكس.",
        "nav_home" to "الرئيسية", "nav_downloads" to "التنزيلات", "nav_settings" to "الإعدادات",
        "chapters" to "فصل", "search_ch" to "ابحث في الفصول…", "range" to "تحديد نطاق", "all" to "تحديد الكل", "none" to "مسح",
        "fmt_single" to "ملف CBZ واحد", "fmt_single_d" to "كل الفصول مدمجة في ملف واحد",
        "fmt_zip" to "مجلد ملفات CBZ", "fmt_zip_d" to "ملف CBZ لكل فصل داخل مجلد باسم المانجا",
        "download" to "تحميل", "pick_one" to "اختر فصلاً واحداً على الأقل.", "chapter" to "الفصل", "back" to "رجوع",
        "st_running" to "جاري التحميل…", "st_done" to "تم! الملف محفوظ في التنزيلات.", "st_error" to "فشل التحميل", "st_cancelled" to "تم الإلغاء",
        "s_queued" to "في الانتظار", "s_active" to "جاري التحميل", "s_done" to "تم", "s_partial" to "صفحات ناقصة", "s_failed" to "فشل",
        "cancel" to "إلغاء", "open_dl" to "فتح التنزيلات", "saved_in" to "المحفوظ في",
        "busy" to "هناك تحميل يعمل بالفعل.",
        "err_BAD_URL" to "هذا لا يبدو رابطاً صحيحاً.",
        "err_NO_CHAPTERS" to "لم يتم العثور على فصول. استخدم صفحة المانجا (وليس فصلاً)، أو جرّب وضع المتصفح، وقد يتطلب الموقع تسجيل دخول أو CAPTCHA.",
        "err_NO_PAGES" to "لم يتم العثور على صور لهذا الفصل.",
        "err_NETWORK" to "لا يوجد اتصال بالإنترنت أو الموقع غير متاح.",
        "err_HTTP" to "الموقع رد بخطأ",
        "err_BROWSER" to "فشل وضع المتصفح في تحميل الصفحة.",
        "hist_empty" to "لا يوجد شيء محمّل بعد.", "hist_clear" to "مسح القائمة", "files" to "ملف",
        "set_lang" to "اللغة", "set_theme" to "المظهر", "theme_dark" to "داكن", "theme_light" to "فاتح", "theme_system" to "النظام",
        "set_browser" to "استخدم وضع المتصفح دائماً", "set_cache" to "مسح الكاش", "cache_done" to "تم مسح الكاش",
        "about" to "MangaVault يحفظ المانجا كملفات CBZ. حمّل فقط من المصادر التي تسمح بذلك، وادعم المؤلفين بشراء النسخ الرسمية.",
    )
}
