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
import java.time.ZoneId;


/**
 * MIS 自動化練習系統 v4
 *
 * 主來源：arXiv API（學術論文，免費無需 key）
 * 備援：Hacker News → Wikipedia
 *
 * arXiv 分類：
 *   cs.AI   — Artificial Intelligence
 *   cs.LG   — Machine Learning
 *   cs.SE   — Software Engineering
 *   cs.IR   — Information Retrieval (MIS 核心)
 *   cs.CY   — Computers & Society
 *   cs.HC   — Human-Computer Interaction
 *   econ.GN — General Economics
 *   stat.ML — Statistics / ML
 *
 * 環境變數：NOTION_TOKEN, DATABASE_ID
 */
public class NotionDevToSync {

    private static final Random     RANDOM = new Random();
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    // ── arXiv 分類清單 ────────────────────────────────────────────────────────
    private static final List<String[]> ARXIV_CATS = List.of(
        new String[]{"cs.AI",   "🤖 Artificial Intelligence"},
        new String[]{"cs.LG",   "🧠 Machine Learning"},
        new String[]{"cs.SE",   "💻 Software Engineering"},
        new String[]{"cs.IR",   "🗂 Information Retrieval"},
        new String[]{"cs.CY",   "🌐 Computers & Society"},
        new String[]{"cs.HC",   "🖱 Human-Computer Interaction"},
        new String[]{"econ.GN", "📈 General Economics"},
        new String[]{"stat.ML", "📊 Statistics / ML"}
    );

    public static void main(String[] args) throws Exception {

        String notionToken = System.getenv("NOTION_TOKEN");
        String databaseId  = System.getenv("DATABASE_ID");
        if (notionToken == null || notionToken.isBlank())
            throw new IllegalStateException("❌ 缺少環境變數 NOTION_TOKEN");
        if (databaseId == null || databaseId.isBlank())
            throw new IllegalStateException("❌ 缺少環境變數 DATABASE_ID");

        // ── 依序嘗試三個來源 ─────────────────────────────────────────────────
        Article article = null;

        System.out.println("📡 嘗試來源 [arXiv]...");
        try { article = fetchFromArxiv(); } catch (Exception e) { System.out.println("⚠️  arXiv 失敗：" + e.getMessage()); }

        if (article == null) {
            System.out.println("📡 嘗試來源 [Hacker News]...");
            try { article = fetchFromHackerNews(); } catch (Exception e) { System.out.println("⚠️  HN 失敗：" + e.getMessage()); }
        }

        if (article == null) {
            System.out.println("📡 嘗試來源 [Wikipedia]...");
            try { article = fetchFromWikipedia(); } catch (Exception e) { System.out.println("⚠️  Wikipedia 失敗：" + e.getMessage()); }
        }

        if (article == null)
            throw new RuntimeException("❌ 三個來源都失敗，請檢查網路");

        System.out.println("✅ 來源：" + article.source);
        System.out.println("📰 標題：" + article.title);
        System.out.println("🔗 網址：" + article.url);
        System.out.println("🏷  標籤：" + article.tags);

        // ── 時間 ─────────────────────────────────────────────────────────────
        // 加上 ZoneId 確保抓到的是台北的今天
        String today    = LocalDate.now(java.time.ZoneId.of("Asia/Taipei")).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dateTime = today + "T08:00:00.000+08:00";

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
            "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [\n" +
            "      { \"type\": \"mention\", \"mention\": { \"type\": \"date\", \"date\": { \"start\": \"" + dateTime + "\" } }, \"annotations\": { \"color\": \"blue_background\" } },\n" +
            "      { \"type\": \"text\", \"text\": { \"content\": \"  📚 起來練習 MIS 囉！\" } }\n" +
            "    ] } },\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            h3("💡 我的見解") + ",\n" + emptyP() + ",\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            h3("📝 文章概要") + ",\n" + emptyP() + ",\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            h3("✨ 特別單字") + ",\n" + emptyP() + ",\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            h3("📌 原文複製") + ",\n" + emptyP() + "\n" +
            "  ]\n" +
            "}";

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
    //  來源 1：arXiv  (Atom/XML，免費無需 key)
    //  API 文件：https://arxiv.org/help/api
    // ════════════════════════════════════════════════════════
    private static Article fetchFromArxiv() throws Exception {
        // 隨機選一個分類
        List<String[]> cats = new ArrayList<>(ARXIV_CATS);
        Collections.shuffle(cats, RANDOM);
        String[] chosen = cats.get(0);
        String catId    = chosen[0];
        String catLabel = chosen[1];

        // start 隨機偏移，增加多樣性（arXiv 每天有大量新論文）
        int start = RANDOM.nextInt(30);
        String url = "https://export.arxiv.org/api/query"
                   + "?search_query=cat:" + catId
                   + "&sortBy=submittedDate&sortOrder=descending"
                   + "&start=" + start + "&max_results=10";

        System.out.println("   分類：" + catLabel + " (" + catId + ")，offset=" + start);
        String xml = get(url);

        // 解析 Atom XML：抓所有 <entry> 區塊
        List<String> entries = splitXmlEntries(xml);
        if (entries.isEmpty()) return null;

        // 隨機選一篇
        Collections.shuffle(entries, RANDOM);
        for (String entry : entries) {
            String title    = xmlTag(entry, "title").replaceAll("\\s+", " ").trim();
            String arxivUrl = xmlAttr(entry, "rel=\"related\"", "href");
            // 備援：從 <id> 取連結
            if (arxivUrl.isBlank()) {
                String id = xmlTag(entry, "id").trim();
                if (!id.isBlank()) arxivUrl = id.replace("http://", "https://");
            }
            // 把 /abs/ 換成 abs（有時 API 回傳 /pdf/）
            arxivUrl = arxivUrl.replace("/pdf/", "/abs/");
            if (title.isBlank() || arxivUrl.isBlank()) continue;

            // 取作者（前兩位）
            List<String> authors = xmlTagAll(entry, "name");
            String authorStr = authors.isEmpty() ? "" :
                authors.size() == 1 ? authors.get(0) :
                authors.get(0) + " et al.";

            String tags = catLabel + (authorStr.isBlank() ? "" : " · " + authorStr);
            return new Article(title, arxivUrl, tags, "arXiv / " + catId);
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  來源 2：Hacker News
    // ════════════════════════════════════════════════════════
    private static Article fetchFromHackerNews() throws Exception {
        String listResp = get("https://hacker-news.firebaseio.com/v0/topstories.json");
        List<String> ids = new ArrayList<>();
        for (String tok : listResp.replaceAll("[\\[\\]\\s]", "").split(","))
            if (!tok.isBlank()) ids.add(tok.trim());
        if (ids.isEmpty()) return null;

        Collections.shuffle(ids.subList(0, Math.min(100, ids.size())), RANDOM);
        for (String id : ids.subList(0, Math.min(15, ids.size()))) {
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
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String resp  = get("https://en.wikipedia.org/api/rest_v1/feed/featured/" + today);

        String tfaBlock = extractBlock(resp, "\"tfa\"");
        if (!tfaBlock.isBlank()) {
            String title = extractStr(tfaBlock, "normalizedtitle");
            if (title.isBlank()) title = extractStr(tfaBlock, "title");
            String desktopBlock = extractBlock(resp.substring(resp.indexOf("\"tfa\"")), "\"desktop\"");
            String link = extractStr(desktopBlock, "page");
            if (link.isBlank()) link = "https://en.wikipedia.org/wiki/" + title.replace(" ", "_");
            String desc = extractStr(tfaBlock, "description");
            if (desc.length() > 80) desc = desc.substring(0, 77) + "...";
            if (!title.isBlank())
                return new Article(title, link, desc.isBlank() ? "Wikipedia Featured Article" : desc, "Wikipedia Featured");
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  XML 工具（arXiv 回傳 Atom XML）
    // ════════════════════════════════════════════════════════

    /** 拆分 <entry>...</entry> 區塊 */
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

    /** 取第一個 <tag>content</tag> 的文字內容（去掉巢狀 tag）*/
    private static String xmlTag(String xml, String tag) {
        String open  = "<" + tag + ">";
        String close = "</" + tag + ">";
        int s = xml.indexOf(open);
        if (s < 0) {
            // 嘗試有 attribute 的 tag，例如 <title type="text">
            s = xml.indexOf("<" + tag + " ");
            if (s < 0) return "";
            s = xml.indexOf(">", s);
            if (s < 0) return "";
            s++; // skip >
        } else {
            s += open.length();
        }
        int e = xml.indexOf(close, s);
        if (e < 0) return "";
        // 去掉內層 XML tag
        return xml.substring(s, e).replaceAll("<[^>]+>", "").trim();
    }

    /** 取特定 attribute 的值，例如 rel="related" 旁的 href */
    private static String xmlAttr(String xml, String attrMatch, String attrName) {
        int idx = xml.indexOf(attrMatch);
        if (idx < 0) return "";
        // 往回找 < 確認是同一個 tag
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

    /** 取所有同名 tag 的文字內容（例如多個 <name>）*/
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
            if (c == '\\' && i+1 < json.length()) {
                char n = json.charAt(++i);
                switch(n) {
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
    //  HTTP GET
    // ════════════════════════════════════════════════════════
    private static String get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "*/*")
                .header("User-Agent", "MIS-Practice-Bot/4.0")
                .GET().build();
        HttpResponse<String> res = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() >= 400)
            throw new RuntimeException("HTTP " + res.statusCode() + " → " + url);
        return res.body();
    }

    // ════════════════════════════════════════════════════════
    //  Notion Block 輔助
    // ════════════════════════════════════════════════════════
    private static String h3(String text) {
        return "    { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \"" + escapeJson(text) + "\" } }] } }";
    }
    private static String emptyP() {
        return "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [] } }";
    }
    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    record Article(String title, String url, String tags, String source) {}
}