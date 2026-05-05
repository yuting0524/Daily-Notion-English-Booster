import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class NotionDevToSync {

    private static final Random RANDOM = new Random();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ZoneId TPE = ZoneId.of("Asia/Taipei");

    // ── arXiv 分類 ────────────────────────────────────────────
    private static final List<String[]> ARXIV_CATS = List.of(
            new String[] { "cs.AI", "🤖 Artificial Intelligence" },
            new String[] { "cs.LG", "🧠 Machine Learning" },
            new String[] { "cs.SE", "💻 Software Engineering" },
            new String[] { "cs.IR", "🗂 Information Retrieval" },
            new String[] { "cs.CY", "🌐 Computers & Society" },
            new String[] { "cs.HC", "🖱 Human-Computer Interaction" },
            new String[] { "stat.ML", "📊 Statistics / ML" });

    record Article(String title, String url, String tags, String source) {
    }

    // ════════════════════════════════════════════════════════
    // MAIN 執行入口
    // ════════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {
        String notionToken = requireEnv("NOTION_TOKEN");
        String databaseId = requireEnv("DATABASE_ID");
        String userId = requireEnv("USER_ID");

        Article article = fetchWithFallback();
        System.out.println("✅ 來源：" + article.source());
        System.out.println("📰 標題：" + article.title());
        System.out.println("🔗 網址：" + article.url());
        System.out.println("🏷  標籤：" + article.tags());

        // cron: "0 0 * * *" = UTC 00:00 = 台北 08:00
        // 所以 LocalDate.now(TPE) 就是台北今天，直接寫當天 08:00 不會過期
        String today = LocalDate.now(TPE).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dateTime = today + "T08:00:00.000+08:00";
        System.out.println("📅 寫入日期：" + dateTime);

        String notionBody = NotionBuilder.buildBody(databaseId, article, dateTime, userId);
        postToNotion(notionToken, notionBody);
    }

    // ════════════════════════════════════════════════════════
    // 爬蟲：來源 Fallback 邏輯
    // ════════════════════════════════════════════════════════
    private static Article fetchWithFallback() {
        System.out.println("📡 嘗試來源 [arXiv]...");
        try {
            Article article = fetchFromArxiv();
            if (article != null)
                return article;
        } catch (Exception e) {
            System.out.println("⚠️  arXiv 失敗：" + e.getMessage());
        }

        System.out.println("📡 嘗試來源 [Hacker News]...");
        try {
            Article article = fetchFromHackerNews();
            if (article != null)
                return article;
        } catch (Exception e) {
            System.out.println("⚠️  HN 失敗：" + e.getMessage());
        }

        System.out.println("📡 嘗試來源 [Wikipedia]...");
        try {
            Article article = fetchFromWikipedia();
            if (article != null)
                return article;
        } catch (Exception e) {
            System.out.println("⚠️  Wikipedia 失敗：" + e.getMessage());
        }

        throw new RuntimeException("❌ 三個來源都失敗，請檢查網路");
    }

    // ════════════════════════════════════════════════════════
    // 來源 1：arXiv
    // ════════════════════════════════════════════════════════
    private static Article fetchFromArxiv() throws Exception {
        List<String[]> cats = new ArrayList<>(ARXIV_CATS);
        Collections.shuffle(cats, RANDOM);
        String[] chosen = cats.get(0);
        String catId = chosen[0], catLabel = chosen[1];

        int offset = RANDOM.nextInt(30);
        String url = "https://export.arxiv.org/api/query?search_query=cat:" + catId
                + "&sortBy=submittedDate&sortOrder=descending&start=" + offset + "&max_results=10";

        System.out.println("   分類：" + catLabel + " (" + catId + ")，offset=" + offset);
        String xml = get(url);
        List<String> entries = XmlUtil.splitEntries(xml);

        if (entries.isEmpty())
            return null;

        Collections.shuffle(entries, RANDOM);
        for (String entry : entries) {
            String title = XmlUtil.tag(entry, "title").replaceAll("\\s+", " ").trim();
            String link = resolveArxivUrl(entry);
            if (title.isBlank() || link.isBlank())
                continue;

            List<String> authors = XmlUtil.tagAll(entry, "name");
            String authorStr = authors.isEmpty() ? ""
                    : (authors.size() == 1 ? authors.get(0) : authors.get(0) + " et al.");
            String tags = catLabel + (authorStr.isBlank() ? "" : " · " + authorStr);

            return new Article(title, link, tags, "arXiv / " + catId);
        }
        return null;
    }

    private static String resolveArxivUrl(String entry) {
        String url = XmlUtil.attr(entry, "rel=\"related\"", "href");
        if (url.isBlank()) {
            String id = XmlUtil.tag(entry, "id").trim();
            if (!id.isBlank())
                url = id.replace("http://", "https://");
        }
        return url.replace("/pdf/", "/abs/");
    }

    // ════════════════════════════════════════════════════════
    // 來源 2：Hacker News
    // ════════════════════════════════════════════════════════
    private static Article fetchFromHackerNews() throws Exception {
        String listResp = get("https://hacker-news.firebaseio.com/v0/topstories.json");
        List<String> ids = new ArrayList<>();
        for (String tok : listResp.replaceAll("[\\[\\]\\s]", "").split(",")) {
            if (!tok.isBlank())
                ids.add(tok.trim());
        }
        if (ids.isEmpty())
            return null;

        int cap = Math.min(100, ids.size());
        Collections.shuffle(ids.subList(0, cap), RANDOM);

        for (String id : ids.subList(0, Math.min(15, cap))) {
            String detail = get("https://hacker-news.firebaseio.com/v0/item/" + id + ".json");
            String title = JsonUtil.extractStr(detail, "title");
            String link = JsonUtil.extractStr(detail, "url");

            if (title.isBlank() || link.isBlank())
                continue;
            return new Article(title, link, "Hacker News Top Story", "Hacker News");
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    // 來源 3：Wikipedia 每日精選
    // ════════════════════════════════════════════════════════
    private static Article fetchFromWikipedia() throws Exception {
        String today = LocalDate.now(TPE).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String resp = get("https://en.wikipedia.org/api/rest_v1/feed/featured/" + today);

        String tfaBlock = JsonUtil.extractBlock(resp, "\"tfa\"");
        if (tfaBlock.isBlank())
            return null;

        String title = JsonUtil.extractStr(tfaBlock, "normalizedtitle");
        if (title.isBlank())
            title = JsonUtil.extractStr(tfaBlock, "title");
        if (title.isBlank())
            return null;

        String desktopBlock = JsonUtil.extractBlock(resp.substring(resp.indexOf("\"tfa\"")), "\"desktop\"");
        String link = JsonUtil.extractStr(desktopBlock, "page");
        if (link.isBlank())
            link = "https://en.wikipedia.org/wiki/" + title.replace(" ", "_");

        String desc = JsonUtil.extractStr(tfaBlock, "description");
        if (desc.length() > 80)
            desc = desc.substring(0, 77) + "...";

        return new Article(title, link, desc.isBlank() ? "Wikipedia Featured Article" : desc, "Wikipedia Featured");
    }

    // ════════════════════════════════════════════════════════
    //  Notion：組 Request Body
    // ════════════════════════════════════════════════════════
        private static String buildNotionBody(String databaseId, Article a, String dateTime) {
    String safeTitle = escapeJson(a.title);
    String safeUrl   = escapeJson(a.url);
    String safeRich  = escapeJson(a.source + "  |  " + a.tags);
    String userId = System.getenv("NOTION_USER_ID");

    return "{\n" +
        "  \"parent\": { \"database_id\": \"" + databaseId + "\" },\n" +
        "  \"properties\": {\n" +
        "    \"Name\": { \"title\": [{ \"text\": { \"content\": \"" + safeTitle + "\" } }] },\n" +
        "    \"URL\": { \"url\": \"" + safeUrl + "\" },\n" +
        "    \"Date\": { \"date\": { \"start\": \"" + dateTime + "\" } },\n" +
        "    \"Article_Title\": { \"rich_text\": [{ \"text\": { \"content\": \"" + safeRich + "\" } }] }\n" +
        "  },\n" +
        "  \"children\": [\n" +

        // 🔔 這次真的會響的提醒 (同時標記 妳 + 時間)
        "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [" +
        "      { \"type\": \"text\", \"text\": { \"content\": \"(੭ ᐕ)੭⁾⁾*嗨 \" } }," +
        "      { \"type\": \"mention\", \"mention\": { \"type\": \"user\", \"user\": { \"id\": \"" + userId + "\" } } }," +
        "      { \"type\": \"text\", \"text\": { \"content\": \"！新文章來ㄌ!：\" } }," +
        "      { \"type\": \"mention\", \"mention\": { \"type\": \"date\", \"date\": { \"start\": \"" + dateTime + "\" } } }" +
        "    ] } },\n" +

        divider() + ",\n" +

        // ──────────────── 文章概要 ────────────────
        morandiToggle("文章概要", "brown_background",
            section("1. 我的作答") + "," +
            section("2. AI 批閱") + "," +
            section("3. AI 解析")
        ) + ",\n" +

        divider() + ",\n" +

        // ──────────────── 我的見解 ────────────────
        morandiToggle("我的見解", "gray_background",
            section("1. 我的作答") + "," +
            section("2. AI 批閱") + "," +
            section("3. AI 解析")
        ) + ",\n" +

        divider() + ",\n" +

        // ──────────────── 反思互動區 ────────────────
        morandiToggle("反思互動區", "orange_background",
            section("1. AI 提問") + "," +
            section("2. 我的作答") + "," +
            section("3. AI 批閱") + "," +
            section("4. AI 解析")
        ) + ",\n" +

        divider() + ",\n" +

        // ──────────────── 特別單字 ────────────────
        morandiToggle("特別單字", "yellow_background",
            section("重點單字紀錄")
        ) + ",\n" +

        divider() + ",\n" +

        // ──────────────── 原文複製 ────────────────
        morandiToggle("原文複製", "default",
            spacer()
        ) + "\n" +

        "  ]\n" +
        "}";
}
    private static String section(String title) {
        return subHeading(title) + "," +
            spacer() + "," +   // ← 可點擊輸入區
            divider();
    }

    // --- 🛠️ 這是新的輔助方法，請務必一起替換/新增 ---

    // 1. 中標題 (Heading 3)：讓「我的作答」看起來很顯眼
    private static String subHeading(String text) {
    return "{ \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { " +
           "\"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \"" + text + "\" } }] } }";
    }
    private static String spacer() {
    return "{ \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { " +
           "\"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \" \" } }] } }";
    }

    // ════════════════════════════════════════════════════════
    //  Notion：送出請求
    // ════════════════════════════════════════════════════════
    private static void postToNotion(String token, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/pages"))
                .header("Authorization", "Bearer " + token)
                .header("Notion-Version", "2022-06-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());

        System.out.println("\n══════════════════════════════════════");
        System.out.println("📬 Notion 狀態碼：" + res.statusCode());
        System.out.println(res.body());
        System.out.println("══════════════════════════════════════");

        if (res.statusCode() == 200) {
            System.out.println("🎉 成功寫入 Notion！");
        } else {
            System.err.println("❌ 寫入失敗");
            System.exit(1);
        }
    }

    // ════════════════════════════════════════════════════════
    // 共用工具 (HTTP & 環境變數)
    // ════════════════════════════════════════════════════════
    private static String requireEnv(String key) {
        String val = System.getenv(key);
        if (val == null || val.isBlank())
            throw new IllegalStateException("❌ 缺少環境變數 " + key);
        return val;
    }

    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "*/*")
                .header("User-Agent", "MIS-Practice-Bot/5.0")
                .GET().build();
        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400)
            throw new RuntimeException("HTTP " + res.statusCode() + " → " + url);
        return res.body();
    }

    // ════════════════════════════════════════════════════════
    // 內部類別：Notion JSON 區塊產生器
    // ════════════════════════════════════════════════════════
    private static class NotionBuilder {

        public static String buildBody(String databaseId, Article a, String dateTime, String userId) {
            String safeTitle = JsonUtil.escape(a.title());
            String safeUrl = JsonUtil.escape(a.url());
            String safeRich = JsonUtil.escape(a.source() + "  |  " + a.tags());

            return "{\n" +
                    "  \"parent\": { \"database_id\": \"" + databaseId + "\" },\n" +
                    "  \"properties\": {\n" +
                    "    \"Name\": { \"title\": [{ \"text\": { \"content\": \"" + safeTitle + "\" } }] },\n" +
                    "    \"URL\": { \"url\": \"" + safeUrl + "\" },\n" +
                    "    \"Date\": { \"date\": { \"start\": \"" + dateTime + "\" } },\n" +
                    "    \"Article_Title\": { \"rich_text\": [{ \"text\": { \"content\": \"" + safeRich + "\" } }] }\n"
                    +
                    "  },\n" +
                    "  \"children\": [\n" +
                    "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [" +
                    "      { \"type\": \"text\", \"text\": { \"content\": \"🔔 每日文章已更新，請查收：\" } }," +
                    "      { \"type\": \"mention\", \"mention\": { \"type\": \"user\", \"user\": { \"id\": \"" + userId
                    + "\" } } }" +
                    "    ] } },\n" +
                    divider() + ",\n" +
                    morandiToggle("文章概要", "brown_background",
                            section("1. 我的作答") + "," + section("2. AI 批閱") + "," + section("3. AI 解析"))
                    + ",\n" +
                    divider() + ",\n" +
                    morandiToggle("我的見解", "gray_background",
                            section("1. 我的作答") + "," + section("2. AI 批閱") + "," + section("3. AI 解析"))
                    + ",\n" +
                    divider() + ",\n" +
                    morandiToggle("反思互動區", "orange_background",
                            section("1. AI 提問") + "," + section("2. 我的作答") + "," + section("3. AI 批閱") + ","
                                    + section("4. AI 解析"))
                    + ",\n" +
                    divider() + ",\n" +
                    morandiToggle("特別單字", "yellow_background", section("重點單字紀錄")) + ",\n" +
                    divider() + ",\n" +
                    morandiToggle("原文複製", "default", spacer()) + "\n" +
                    "  ]\n" +
                    "}";
        }

        private static String section(String title) {
            return subHeading(title) + "," + spacer() + "," + divider();
        }

        private static String subHeading(String text) {
            return "{ \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \""
                    + text + "\" } }] } }";
        }

        private static String spacer() {
            return "{ \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \" \" } }] } }";
        }

        private static String morandiToggle(String title, String color, String childrenJson) {
            return "{ \"object\": \"block\", \"type\": \"toggle\", \"toggle\": { \"rich_text\": [{\"type\": \"text\", \"text\": {\"content\": \""
                    + title + "\"}, \"annotations\": {\"bold\": true}}], \"color\": \"" + color + "\", \"children\": ["
                    + childrenJson + "] } }";
        }

        private static String divider() {
            return "{ \"object\": \"block\", \"type\": \"divider\", \"divider\": {} }";
        }
    }

    // ════════════════════════════════════════════════════════
    // 內部類別：XML 工具
    // ════════════════════════════════════════════════════════
    private static class XmlUtil {

        public static List<String> splitEntries(String xml) {
            List<String> result = new ArrayList<>();
            int pos = 0;
            while (true) {
                int start = xml.indexOf("<entry>", pos);
                if (start < 0)
                    break;
                int end = xml.indexOf("</entry>", start);
                if (end < 0)
                    break;
                result.add(xml.substring(start, end + 8));
                pos = end + 8;
            }
            return result;
        }

        public static String tag(String xml, String tag) {
            String open = "<" + tag + ">", close = "</" + tag + ">";
            int s = xml.indexOf(open);
            if (s < 0) {
                s = xml.indexOf("<" + tag + " ");
                if (s < 0)
                    return "";
                s = xml.indexOf(">", s);
                if (s < 0)
                    return "";
                s++;
            } else {
                s += open.length();
            }
            int e = xml.indexOf(close, s);
            return e < 0 ? "" : xml.substring(s, e).replaceAll("<[^>]+>", "").trim();
        }

        public static String attr(String xml, String attrMatch, String attrName) {
            int idx = xml.indexOf(attrMatch);
            if (idx < 0)
                return "";
            int tagStart = xml.lastIndexOf("<", idx), tagEnd = xml.indexOf(">", idx);
            if (tagStart < 0 || tagEnd < 0)
                return "";
            String tagStr = xml.substring(tagStart, tagEnd + 1);
            int ai = tagStr.indexOf(attrName + "=\"");
            if (ai < 0)
                return "";
            int vs = ai + attrName.length() + 2, ve = tagStr.indexOf("\"", vs);
            return ve < 0 ? "" : tagStr.substring(vs, ve);
        }

        public static List<String> tagAll(String xml, String tag) {
            List<String> result = new ArrayList<>();
            String open = "<" + tag + ">", close = "</" + tag + ">";
            int pos = 0;
            while (true) {
                int s = xml.indexOf(open, pos);
                if (s < 0)
                    break;
                s += open.length();
                int e = xml.indexOf(close, s);
                if (e < 0)
                    break;
                result.add(xml.substring(s, e).trim());
                pos = e + close.length();
            }
            return result;
        }
    }

    // ════════════════════════════════════════════════════════
    // 內部類別：JSON 工具
    // ════════════════════════════════════════════════════════
    private static class JsonUtil {

        public static String extractStr(String json, String key) {
            int ki = json.indexOf("\"" + key + "\"");
            if (ki < 0)
                return "";
            int ci = json.indexOf(":", ki);
            if (ci < 0)
                return "";
            int vs = json.indexOf("\"", ci + 1);
            if (vs < 0)
                return "";
            StringBuilder sb = new StringBuilder();
            for (int i = vs + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char n = json.charAt(++i);
                    switch (n) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        default -> {
                            sb.append('\\');
                            sb.append(n);
                        }
                    }
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        public static String extractBlock(String json, String keyPattern) {
            int ki = json.indexOf(keyPattern);
            if (ki < 0)
                return "";
            int ci = json.indexOf(":", ki);
            if (ci < 0)
                return "";
            int bs = json.indexOf("{", ci);
            if (bs < 0)
                return "";
            int depth = 0;
            for (int i = bs; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '{')
                    depth++;
                else if (c == '}' && --depth == 0)
                    return json.substring(bs, i + 1);
            }
            return "";
        }

        public static String escape(String s) {
            if (s == null)
                return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }
    }
// 這是妳剛才不小心弄丟的工具函式，用來處理 JSON 格式的特殊字元
private static String escapeJson(String input) {
    if (input == null) return "";
    return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
}
// === 以下是妳消失的工具箱，請補在 class 的最後面 ===

private static String divider() {
    return "{ \"object\": \"block\", \"type\": \"divider\", \"divider\": {} }";
}

private static String morandiToggle(String title, String color, String childrenJson) {
    return "{ \"object\": \"block\", \"type\": \"toggle\", \"toggle\": { " +
           "\"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \"" + escapeJson(title) + "\" }, \"annotations\": { \"color\": \"" + color + "\" } }], " +
           "\"children\": [" + childrenJson + "] } }";
}


}
