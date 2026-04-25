import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * MIS 自動化練習系統 v3
 *
 * 三個免費來源輪流使用，互相 fallback：
 *   1. dev.to        — 技術文章 (state=fresh，不用 top= 避免空回傳)
 *   2. Hacker News   — 科技 / 創業 / CS 精選
 *   3. Wikipedia     — 每日精選文章（英文知識廣度極高）
 *
 * 環境變數：
 *   NOTION_TOKEN  - Notion Integration Token
 *   DATABASE_ID   - Notion Database ID
 */
public class NotionDevToSync {

    private static final Random RANDOM = new Random();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    // ── dev.to 主題（已驗證 slug 存在）──────────────────────────────────────
    private static final List<String> DEVTO_TAGS = List.of(
        "javascript", "python", "webdev", "programming",
        "ai", "machinelearning", "computerscience", "architecture",
        "opensource", "productivity", "startup", "product",
        "agile", "career", "learning", "tutorial", "devops"
    );

    public static void main(String[] args) throws Exception {

        String notionToken = System.getenv("NOTION_TOKEN");
        String databaseId  = System.getenv("DATABASE_ID");
        if (notionToken == null || notionToken.isBlank())
            throw new IllegalStateException("❌ 缺少環境變數 NOTION_TOKEN");
        if (databaseId == null || databaseId.isBlank())
            throw new IllegalStateException("❌ 缺少環境變數 DATABASE_ID");

        // ── 隨機決定先試哪個來源，失敗自動換下一個 ─────────────────────────
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2));
        Collections.shuffle(order, RANDOM);

        Article article = null;
        for (int source : order) {
            System.out.println("📡 嘗試來源 [" + sourceName(source) + "]...");
            try {
                article = switch (source) {
                    case 0 -> fetchFromDevTo();
                    case 1 -> fetchFromHackerNews();
                    case 2 -> fetchFromWikipedia();
                    default -> null;
                };
            } catch (Exception e) {
                System.out.println("⚠️  來源失敗：" + e.getMessage());
            }
            if (article != null) {
                System.out.println("✅ 成功取得文章來自 [" + sourceName(source) + "]");
                break;
            }
            System.out.println("⚠️  來源無結果，換下一個...");
        }

        if (article == null)
            throw new RuntimeException("❌ 三個來源都失敗了，請檢查網路或 API");

        System.out.println("📰 標題：" + article.title);
        System.out.println("🔗 網址：" + article.url);
        System.out.println("🏷  標籤：" + article.tags);

        // ── 時間 ─────────────────────────────────────────────────────────────
        String today    = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dateTime = today + "T08:00:00.000+08:00";

        // ── 組 Notion Request Body ────────────────────────────────────────────
        String safeTitle = escapeJson(article.title);
        String safeUrl   = escapeJson(article.url);
        String safeRich  = escapeJson(article.source + "  |  " + article.tags);

        String body = "{\n" +
            "  \"parent\": { \"database_id\": \"" + databaseId + "\" },\n" +
            "  \"icon\": { \"type\": \"emoji\", \"emoji\": \"📚\" },\n" +
            "  \"properties\": {\n" +
            "    \"Name\": { \"title\": [{ \"text\": { \"content\": \"" + safeTitle + "\" } }] },\n" +
            "    \"URL\": { \"url\": \"" + safeUrl + "\" },\n" +
            "    \"Date\": { \"date\": { \"start\": \"" + dateTime + "\" } },\n" +
            "    \"Article_Title\": { \"rich_text\": [{ \"text\": { \"content\": \"" + safeRich + "\" } }] }\n" +
            "  },\n" +
            "  \"children\": [\n" +
            // @remind mention
            "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [\n" +
            "      { \"type\": \"mention\", \"mention\": { \"type\": \"date\", \"date\": { \"start\": \"" + dateTime + "\" } }, \"annotations\": { \"color\": \"blue_background\" } },\n" +
            "      { \"type\": \"text\", \"text\": { \"content\": \"  📚 起來練習 MIS 囉！\" } }\n" +
            "    ] } },\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            heading3Block("💡 我的見解") + ",\n" +
            emptyParagraph() + ",\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            heading3Block("📝 文章概要") + ",\n" +
            emptyParagraph() + ",\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            heading3Block("✨ 特別單字") + ",\n" +
            emptyParagraph() + ",\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            heading3Block("📌 原文複製") + ",\n" +
            emptyParagraph() + "\n" +
            "  ]\n" +
            "}";

        // ── 呼叫 Notion API ───────────────────────────────────────────────────
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/pages"))
                .header("Authorization", "Bearer " + notionToken)
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
    //  來源 1：dev.to  (state=fresh 避免 top= 空回傳)
    // ════════════════════════════════════════════════════════
    private static Article fetchFromDevTo() throws Exception {
        // 打亂 tag 順序，最多嘗試 6 個
        List<String> tags = new ArrayList<>(DEVTO_TAGS);
        Collections.shuffle(tags, RANDOM);

        for (String tag : tags.subList(0, Math.min(6, tags.size()))) {
            String url = "https://dev.to/api/articles?tag=" + tag
                       + "&per_page=12&state=fresh";
            String resp = get(url);
            List<String> items = splitJsonArray(resp);
            if (items.isEmpty()) continue;

            String item  = items.get(RANDOM.nextInt(items.size()));
            String title = extractStr(item, "title");
            String link  = extractStr(item, "url");
            String tags2 = extractTagArray(item);
            if (title.isBlank() || link.isBlank()) continue;

            return new Article(title, link, tags2, "dev.to / " + tag);
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  來源 2：Hacker News  (官方免費 API，無需 key)
    //  策略：抓 topstories 前 100，隨機選 5 篇試著取詳細資料
    // ════════════════════════════════════════════════════════
    private static Article fetchFromHackerNews() throws Exception {
        String listResp = get("https://hacker-news.firebaseio.com/v0/topstories.json");
        // 回傳是純數字陣列 [123,456,...]
        List<String> ids = new ArrayList<>();
        for (String tok : listResp.replaceAll("[\\[\\]\\s]", "").split(",")) {
            if (!tok.isBlank()) ids.add(tok.trim());
        }
        if (ids.isEmpty()) return null;

        // 從前 100 篇隨機挑，找到第一篇有 url 的文章（部分是 Ask HN，沒有外部連結）
        Collections.shuffle(ids.subList(0, Math.min(100, ids.size())), RANDOM);
        for (String id : ids.subList(0, Math.min(15, ids.size()))) {
            String detail = get("https://hacker-news.firebaseio.com/v0/item/" + id + ".json");
            String title  = extractStr(detail, "title");
            String link   = extractStr(detail, "url");
            if (title.isBlank() || link.isBlank()) continue; // 跳過 Ask HN 等無外連文章
            return new Article(title, link, "Hacker News Top Story", "Hacker News");
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  來源 3：Wikipedia  每日精選文章 (Featured Content API)
    //  永遠有內容，當其他來源都掛掉的最終保底
    // ════════════════════════════════════════════════════════
    private static Article fetchFromWikipedia() throws Exception {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String url   = "https://en.wikipedia.org/api/rest_v1/feed/featured/" + today;
        String resp  = get(url);

        // 嘗試取 tfa (Today's Featured Article)
        String tfaBlock = extractBlock(resp, "\"tfa\"");
        if (!tfaBlock.isBlank()) {
            String title      = extractStr(tfaBlock, "normalizedtitle");
            if (title.isBlank()) title = extractStr(tfaBlock, "title");
            String pageLink   = extractStr(tfaBlock, "content_urls");
            // content_urls 是物件，直接從裡面抓 desktop page
            String desktopBlock = extractBlock(resp.substring(resp.indexOf("\"tfa\"")), "\"desktop\"");
            String link = extractStr(desktopBlock, "page");
            if (link.isBlank()) link = "https://en.wikipedia.org/wiki/" + title.replace(" ", "_");
            if (!title.isBlank()) {
                String description = extractStr(tfaBlock, "description");
                String tags = description.isBlank() ? "Wikipedia Featured Article" : description;
                // 截短 description 避免太長
                if (tags.length() > 80) tags = tags.substring(0, 77) + "...";
                return new Article(title, link, tags, "Wikipedia Featured");
            }
        }

        // fallback: 抓 On This Day 的第一篇
        String onThisDay = extractBlock(resp, "\"onthisday\"");
        if (!onThisDay.isBlank()) {
            String text = extractStr(onThisDay, "text");
            String link = "https://en.wikipedia.org/wiki/Wikipedia:On_this_day/Today";
            if (!text.isBlank()) {
                if (text.length() > 100) text = text.substring(0, 97) + "...";
                return new Article(text, link, "Wikipedia On This Day", "Wikipedia");
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  HTTP GET
    // ════════════════════════════════════════════════════════
    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .header("User-Agent", "MIS-Practice-Bot/3.0")
                .GET().build();
        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400)
            throw new RuntimeException("HTTP " + res.statusCode() + " for " + url);
        return res.body();
    }

    // ════════════════════════════════════════════════════════
    //  JSON 工具（不依賴第三方函式庫）
    // ════════════════════════════════════════════════════════

    /** 將頂層 JSON 陣列拆成個別物件字串 */
    private static List<String> splitJsonArray(String json) {
        List<String> result = new ArrayList<>();
        json = json.trim();
        if (!json.startsWith("[")) return result;
        int depth = 0, start = -1;
        boolean inStr = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i-1) != '\\')) inStr = !inStr;
            if (inStr) continue;
            if (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}') { if (--depth == 0 && start != -1) { result.add(json.substring(start, i+1)); start = -1; } }
        }
        return result;
    }

    /** 取出 key 對應的字串值 */
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
            if (c == '\\' && i+1 < json.length()) {
                char n = json.charAt(i+1);
                switch(n) { case '"': sb.append('"'); case '\\': sb.append('\\'); case 'n': sb.append('\n'); case 't': sb.append('\t'); default: sb.append(c); }
                i++;
            } else if (c == '"') break;
            else sb.append(c);
        }
        return sb.toString();
    }

    /** 取出某個 key 後面的整個物件 block（用於巢狀解析）*/
    private static String extractBlock(String json, String keyPattern) {
        int ki = json.indexOf(keyPattern);
        if (ki < 0) return "";
        int ci = json.indexOf(":", ki);
        if (ci < 0) return "";
        int bs = json.indexOf("{", ci);
        if (bs < 0) return "";
        int depth = 0, end = bs;
        for (int i = bs; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { if (--depth == 0) { end = i; break; } }
        }
        return json.substring(bs, end + 1);
    }

    /** 從 dev.to 回應取出 tag_list */
    private static String extractTagArray(String json) {
        int idx = json.indexOf("\"tag_list\"");
        if (idx < 0) return "general";
        int as = json.indexOf("[", idx);
        int ae = json.indexOf("]", as);
        if (as < 0 || ae < 0) return "general";
        String arr = json.substring(as+1, ae).trim();
        if (arr.isBlank()) return "general";
        List<String> tags = new ArrayList<>();
        int pos = 0;
        while (pos < arr.length()) {
            int q1 = arr.indexOf('"', pos); if (q1 < 0) break;
            int q2 = arr.indexOf('"', q1+1); if (q2 < 0) break;
            tags.add(arr.substring(q1+1, q2));
            pos = q2 + 1;
        }
        return tags.isEmpty() ? "general" : String.join(", ", tags);
    }

    // ════════════════════════════════════════════════════════
    //  Notion Block 輔助
    // ════════════════════════════════════════════════════════
    private static String heading3Block(String text) {
        return "    { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \"" + escapeJson(text) + "\" } }] } }";
    }
    private static String emptyParagraph() {
        return "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [] } }";
    }

    /** JSON 字串轉義 */
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private static String sourceName(int i) {
        return switch(i) { case 0 -> "dev.to"; case 1 -> "Hacker News"; case 2 -> "Wikipedia"; default -> "?"; };
    }

    // ── 資料容器 ─────────────────────────────────────────────────────────────
    record Article(String title, String url, String tags, String source) {}
}