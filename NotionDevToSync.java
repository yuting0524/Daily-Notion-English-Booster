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

/**
 * MIS 自動化練習系統 v5
 *
 * 修正：
 *  - cron 00:00 UTC = 台北 08:00，寫入當天 08:00 提醒不會過期
 *  - 移除 Notion date property 裡的 reminder（API 不支援）
 *  - 移除 econ.GN（經濟/會計類）
 *  - 程式碼整體重構，職責分離更清楚
 */
public class NotionDevToSync {

    private static final Random     RANDOM = new Random();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ZoneId     TPE    = ZoneId.of("Asia/Taipei");

    // ── arXiv 分類（移除 econ.GN）────────────────────────────────────────────
    private static final List<String[]> ARXIV_CATS = List.of(
        new String[]{"cs.AI",  "🤖 Artificial Intelligence"},
        new String[]{"cs.LG",  "🧠 Machine Learning"},
        new String[]{"cs.SE",  "💻 Software Engineering"},
        new String[]{"cs.IR",  "🗂 Information Retrieval"},
        new String[]{"cs.CY",  "🌐 Computers & Society"},
        new String[]{"cs.HC",  "🖱 Human-Computer Interaction"},
        new String[]{"stat.ML","📊 Statistics / ML"}
    );

    // ════════════════════════════════════════════════════════
    //  MAIN
    // ════════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {

        String notionToken = requireEnv("NOTION_TOKEN");
        String databaseId  = requireEnv("DATABASE_ID");

        Article article = fetchWithFallback();
        System.out.println("✅ 來源：" + article.source);
        System.out.println("📰 標題：" + article.title);
        System.out.println("🔗 網址：" + article.url);
        System.out.println("🏷  標籤：" + article.tags);

        // ── 時間計算 ─────────────────────────────────────────────────────────
        // cron: "0 0 * * *" = UTC 00:00 = 台北 08:00
        // 所以 LocalDate.now(TPE) 就是台北今天，直接寫當天 08:00 不會過期
        String today    = LocalDate.now(TPE).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dateTime = today + "T08:00:00.000+08:00";
        System.out.println("📅 寫入日期：" + dateTime);

        String notionBody = buildNotionBody(databaseId, article, dateTime);
        postToNotion(notionToken, notionBody);
    }

    // ════════════════════════════════════════════════════════
    //  Fallback 邏輯：arXiv → Hacker News → Wikipedia
    // ════════════════════════════════════════════════════════
    private static Article fetchWithFallback() throws Exception {
        Article article = null;

        System.out.println("📡 嘗試來源 [arXiv]...");
        try { article = fetchFromArxiv(); }
        catch (Exception e) { System.out.println("⚠️  arXiv 失敗：" + e.getMessage()); }

        if (article == null) {
            System.out.println("📡 嘗試來源 [Hacker News]...");
            try { article = fetchFromHackerNews(); }
            catch (Exception e) { System.out.println("⚠️  HN 失敗：" + e.getMessage()); }
        }

        if (article == null) {
            System.out.println("📡 嘗試來源 [Wikipedia]...");
            try { article = fetchFromWikipedia(); }
            catch (Exception e) { System.out.println("⚠️  Wikipedia 失敗：" + e.getMessage()); }
        }

        if (article == null)
            throw new RuntimeException("❌ 三個來源都失敗，請檢查網路");

        return article;
    }

    // ════════════════════════════════════════════════════════
    //  來源 1：arXiv
    // ════════════════════════════════════════════════════════
    private static Article fetchFromArxiv() throws Exception {
        List<String[]> cats = new ArrayList<>(ARXIV_CATS);
        Collections.shuffle(cats, RANDOM);
        String[] chosen   = cats.get(0);
        String   catId    = chosen[0];
        String   catLabel = chosen[1];

        int    offset = RANDOM.nextInt(30);
        String url    = "https://export.arxiv.org/api/query"
                      + "?search_query=cat:" + catId
                      + "&sortBy=submittedDate&sortOrder=descending"
                      + "&start=" + offset + "&max_results=10";

        System.out.println("   分類：" + catLabel + " (" + catId + ")，offset=" + offset);
        String xml     = get(url);
        List<String> entries = splitXmlEntries(xml);
        if (entries.isEmpty()) return null;

        Collections.shuffle(entries, RANDOM);
        for (String entry : entries) {
            String title = xmlTag(entry, "title").replaceAll("\\s+", " ").trim();
            String link  = resolveArxivUrl(entry);
            if (title.isBlank() || link.isBlank()) continue;

            List<String> authors   = xmlTagAll(entry, "name");
            String       authorStr = formatAuthors(authors);
            String       tags      = catLabel + (authorStr.isBlank() ? "" : " · " + authorStr);
            return new Article(title, link, tags, "arXiv / " + catId);
        }
        return null;
    }

    private static String resolveArxivUrl(String entry) {
        String url = xmlAttr(entry, "rel=\"related\"", "href");
        if (url.isBlank()) {
            String id = xmlTag(entry, "id").trim();
            if (!id.isBlank()) url = id.replace("http://", "https://");
        }
        return url.replace("/pdf/", "/abs/");
    }

    private static String formatAuthors(List<String> authors) {
        if (authors.isEmpty()) return "";
        return authors.size() == 1 ? authors.get(0) : authors.get(0) + " et al.";
    }

    // ════════════════════════════════════════════════════════
    //  來源 2：Hacker News
    // ════════════════════════════════════════════════════════
    private static Article fetchFromHackerNews() throws Exception {
        String   listResp = get("https://hacker-news.firebaseio.com/v0/topstories.json");
        List<String> ids  = new ArrayList<>();
        for (String tok : listResp.replaceAll("[\\[\\]\\s]", "").split(","))
            if (!tok.isBlank()) ids.add(tok.trim());
        if (ids.isEmpty()) return null;

        int cap = Math.min(100, ids.size());
        Collections.shuffle(ids.subList(0, cap), RANDOM);
        for (String id : ids.subList(0, Math.min(15, cap))) {
            String detail = get("https://hacker-news.firebaseio.com/v0/item/" + id + ".json");
            String title  = extractStr(detail, "title");
            String link   = extractStr(detail, "url");
            if (title.isBlank() || link.isBlank()) continue;
            return new Article(title, link, "Hacker News Top Story", "Hacker News");
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  來源 3：Wikipedia 每日精選
    // ════════════════════════════════════════════════════════
    private static Article fetchFromWikipedia() throws Exception {
        String today = LocalDate.now(TPE).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String resp  = get("https://en.wikipedia.org/api/rest_v1/feed/featured/" + today);

        String tfaBlock = extractBlock(resp, "\"tfa\"");
        if (tfaBlock.isBlank()) return null;

        String title = extractStr(tfaBlock, "normalizedtitle");
        if (title.isBlank()) title = extractStr(tfaBlock, "title");
        if (title.isBlank()) return null;

        String desktopBlock = extractBlock(resp.substring(resp.indexOf("\"tfa\"")), "\"desktop\"");
        String link         = extractStr(desktopBlock, "page");
        if (link.isBlank()) link = "https://en.wikipedia.org/wiki/" + title.replace(" ", "_");

        String desc = extractStr(tfaBlock, "description");
        if (desc.length() > 80) desc = desc.substring(0, 77) + "...";
        return new Article(title, link,
            desc.isBlank() ? "Wikipedia Featured Article" : desc,
            "Wikipedia Featured");
    }

    // ════════════════════════════════════════════════════════
    //  Notion：組 Request Body
    // ════════════════════════════════════════════════════════
    private static String buildNotionBody(String databaseId, Article a, String dateTime) {
        String safeTitle = escapeJson(a.title);
        String safeUrl   = escapeJson(a.url);
        String safeRich  = escapeJson(a.source + "  |  " + a.tags);

        return "{\n" +
        "  \"parent\": { \"database_id\": \"" + databaseId + "\" },\n" +
        "  \"icon\": { \"type\": \"emoji\", \"emoji\": \"🌿\" },\n" +
        "  \"properties\": {\n" +
        "    \"Name\": { \"title\": [{ \"text\": { \"content\": \"" + safeTitle + "\" } }] },\n" +
        "    \"URL\": { \"url\": \"" + safeUrl + "\" },\n" +
        "    \"Date\": { \"date\": { \"start\": \"" + dateTime + "\" } },\n" + // 這裡保持最簡單的寫法
        "    \"Article_Title\": { \"rich_text\": [{ \"text\": { \"content\": \"" + safeRich + "\" } }] }\n" +
        "  },\n" +
        "  \"children\": [\n" +

            // @remind mention（觸發 Notion 行事曆提醒的關鍵）
            "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [\n" +
            "      { \"type\": \"mention\",\n" +
            "        \"mention\": { \"type\": \"date\", \"date\": { \"start\": \"" + dateTime + "\" } },\n" +
            "        \"annotations\": { \"color\": \"blue_background\" } },\n" +
            "      { \"type\": \"text\", \"text\": { \"content\": \"  📚 起來練習 MIS 囉！\" } }\n" +
            "    ] } },\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +

            // 二、文章概要 (大地棕)
            divider() + ",\n" +
            morandiToggle("文章概要", "brown_background", 
                kaomojiLine("(੭ ᐕ)੭*⁾⁾", "1. 我的作答：") + "," +
                kaomojiLine("(´•ω•｀)", "2. AI 批閱：") + "," +
                kaomojiLine("(´•ω•｀)", "3. AI 解析：")
            ) + ",\n" +

            // 三、我的見解 (高級灰)
            divider() + ",\n" +
            morandiToggle("我的見解", "gray_background", 
                kaomojiLine("(੭ ᐕ)੭*⁾⁾", "1. 我的作答：") + "," +
                kaomojiLine("(´•ω•｀)", "2. AI 批閱：") + "," +
                kaomojiLine("(´•ω•｀)", "3. AI 解析：")
            ) + ",\n" +

           // 四、反思互動區 (粉橘)
            divider() + ",\n" +
            morandiToggle("反思互動區", "orange_background", 
                kaomojiLine("(´•ω•｀)", "1. AI 提問：") + "," +
                kaomojiLine("(੭ ᐕ)੭*⁾⁾", "2. 我的作答：") + "," +
                kaomojiLine("(´•ω•｀)", "3. AI 批閱：") + "," +
                kaomojiLine("(´•ω•｀)", "4. AI 解析：")
            ) + ",\n" +

            // 五、特別單字 (奶油黃)
            divider() + ",\n" +
            morandiToggle("特別單字", "yellow_background", 
                kaomojiLine("(੭ ᐕ)੭*⁾⁾", "重點單字紀錄：")
            ) + ",\n" +

            // 六、原文複製 (簡潔灰)
            divider() + ",\n" +
            morandiToggle("原文複製", "default", 
                "{\"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [{\"type\": \"text\", \"text\": {\"content\": \"Paste here...\"}, \"annotations\": {\"color\": \"gray\"}}] }}"
            ) + "\n" +
            
            "  ]\n" +
            "}";
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
    //  Notion Block 輔助
    // ════════════════════════════════════════════════════════
    // 生成莫蘭迪色背景的摺疊區塊
    private static String morandiToggle(String title, String color, String childrenJson) {
        return "{" +
            "  \"object\": \"block\"," +
            "  \"type\": \"toggle\"," +
            "  \"toggle\": {" +
            "    \"rich_text\": [{\"type\": \"text\", \"text\": {\"content\": \"" + title + "\"}, \"annotations\": {\"bold\": true}}]," +
            "    \"color\": \"" + color + "\"," +
            "    \"children\": [" + childrenJson + "]" +
            "  }" +
            "}";
    }

    // 顏文字輸入行：(੭ ᐕ)੭*⁾⁾ 是妳，(´•ω•｀) 是 AI
    private static String kaomojiLine(String kaomoji, String label) {
        return "{" +
            "  \"object\": \"block\"," +
            "  \"type\": \"paragraph\"," +
            "  \"paragraph\": {" +
            "    \"rich_text\": [" +
            "      {\"type\": \"text\", \"text\": {\"content\": \"" + kaomoji + " \"}}," +
            "      {\"type\": \"text\", \"text\": {\"content\": \"" + label + " \"}, \"annotations\": {\"bold\": true, \"color\": \"gray\"}}" +
            "    ]" +
            "  }" +
            "}";
    }

    // 分隔線區塊零件
    private static String divider() {
        return "{\"object\": \"block\", \"type\": \"divider\", \"divider\": {}}";
    }

    // ════════════════════════════════════════════════════════
    //  XML 工具
    // ════════════════════════════════════════════════════════
    private static List<String> splitXmlEntries(String xml) {
        List<String> result = new ArrayList<>();
        int pos = 0;
        while (true) {
            int start = xml.indexOf("<entry>", pos);
            if (start < 0) break;
            int end = xml.indexOf("</entry>", start);
            if (end < 0) break;
            result.add(xml.substring(start, end + 8));
            pos = end + 8;
        }
        return result;
    }

    private static String xmlTag(String xml, String tag) {
        String open  = "<" + tag + ">";
        String close = "</" + tag + ">";
        int s = xml.indexOf(open);
        if (s < 0) {
            s = xml.indexOf("<" + tag + " ");
            if (s < 0) return "";
            s = xml.indexOf(">", s);
            if (s < 0) return "";
            s++;
        } else {
            s += open.length();
        }
        int e = xml.indexOf(close, s);
        if (e < 0) return "";
        return xml.substring(s, e).replaceAll("<[^>]+>", "").trim();
    }

    private static String xmlAttr(String xml, String attrMatch, String attrName) {
        int idx = xml.indexOf(attrMatch);
        if (idx < 0) return "";
        int tagStart = xml.lastIndexOf("<", idx);
        int tagEnd   = xml.indexOf(">", idx);
        if (tagStart < 0 || tagEnd < 0) return "";
        String tag = xml.substring(tagStart, tagEnd + 1);
        int ai = tag.indexOf(attrName + "=\"");
        if (ai < 0) return "";
        int vs = ai + attrName.length() + 2;
        int ve = tag.indexOf("\"", vs);
        if (ve < 0) return "";
        return tag.substring(vs, ve);
    }

    private static List<String> xmlTagAll(String xml, String tag) {
        List<String> result = new ArrayList<>();
        String open  = "<" + tag + ">";
        String close = "</" + tag + ">";
        int pos = 0;
        while (true) {
            int s = xml.indexOf(open, pos);
            if (s < 0) break;
            s += open.length();
            int e = xml.indexOf(close, s);
            if (e < 0) break;
            result.add(xml.substring(s, e).trim());
            pos = e + close.length();
        }
        return result;
    }

    // ════════════════════════════════════════════════════════
    //  JSON 工具
    // ════════════════════════════════════════════════════════
    private static String extractStr(String json, String key) {
        int ki = json.indexOf("\"" + key + "\"");
        if (ki < 0) return "";
        int ci = json.indexOf(":", ki);
        if (ci < 0) return "";
        int vs = json.indexOf("\"", ci + 1);
        if (vs < 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = vs + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char n = json.charAt(++i);
                switch (n) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n'  -> sb.append('\n');
                    case 't'  -> sb.append('\t');
                    default   -> { sb.append('\\'); sb.append(n); }
                }
            } else if (c == '"') break;
            else sb.append(c);
        }
        return sb.toString();
    }

    private static String extractBlock(String json, String keyPattern) {
        int ki = json.indexOf(keyPattern);
        if (ki < 0) return "";
        int ci = json.indexOf(":", ki);
        if (ci < 0) return "";
        int bs = json.indexOf("{", ci);
        if (bs < 0) return "";
        int depth = 0;
        for (int i = bs; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return json.substring(bs, i + 1);
        }
        return "";
    }

    // ════════════════════════════════════════════════════════
    //  工具
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

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    record Article(String title, String url, String tags, String source) {}
}