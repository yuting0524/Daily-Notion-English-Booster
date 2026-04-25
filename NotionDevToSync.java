import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

/**
 * MIS 自動化練習系統
 * 從 dev.to 抓取熱門文章並寫入 Notion 資料庫
 *
 * 環境變數：
 *   NOTION_TOKEN  - Notion Integration Token (secret_xxx)
 *   DATABASE_ID   - Notion Database ID
 */
public class NotionDevToSync {

    // ─── 主題清單 ───────────────────────────────────────────────────────────────
    private static final List<String> TOPICS = List.of(
        // 🛠 Hard Tech
        "software-engineering",
        "computerscience",
        "ai",
        "datascience",
        "programming",
        "webdev",
        "architecture",
        // 📊 MIS & Management
        "productmanagement",
        "projectmanagement",
        "informationmanagement",
        "startup",
        "businessintelligence",
        // 🚀 Soft Skills
        "productivity",
        "selfimprovement",
        "career",
        "techtrends"
    );

    // ─── 中文標籤對照（用於 Article_Title 顯示）──────────────────────────────
    private static final java.util.Map<String, String> TOPIC_LABELS = new java.util.HashMap<>();
    static {
        TOPIC_LABELS.put("software-engineering",    "💻 Software Engineering");
        TOPIC_LABELS.put("computerscience",         "🖥 Computer Science");
        TOPIC_LABELS.put("ai",                      "🤖 AI / ML");
        TOPIC_LABELS.put("datascience",             "📊 Data Science");
        TOPIC_LABELS.put("programming",             "🧑‍💻 Programming");
        TOPIC_LABELS.put("webdev",                  "🌐 Web Development");
        TOPIC_LABELS.put("architecture",            "🏗 System Design");
        TOPIC_LABELS.put("productmanagement",       "📦 Product Management");
        TOPIC_LABELS.put("projectmanagement",       "📅 Project Management");
        TOPIC_LABELS.put("informationmanagement",   "🗂 Information Management");
        TOPIC_LABELS.put("startup",                 "🚀 Startup / Entrepreneurship");
        TOPIC_LABELS.put("businessintelligence",    "📈 Business Intelligence");
        TOPIC_LABELS.put("productivity",            "⚡ Productivity");
        TOPIC_LABELS.put("selfimprovement",         "🌱 Self Improvement");
        TOPIC_LABELS.put("career",                  "🎯 Career Advice");
        TOPIC_LABELS.put("techtrends",              "📡 Tech Trends");
    }

    public static void main(String[] args) throws Exception {

        // ─── 讀取環境變數 ─────────────────────────────────────────────────────
        String notionToken = System.getenv("NOTION_TOKEN");
        String databaseId  = System.getenv("DATABASE_ID");

        if (notionToken == null || notionToken.isBlank()) {
            throw new IllegalStateException("❌ 環境變數 NOTION_TOKEN 未設定");
        }
        if (databaseId == null || databaseId.isBlank()) {
            throw new IllegalStateException("❌ 環境變數 DATABASE_ID 未設定");
        }

        HttpClient client = HttpClient.newHttpClient();
        Random random     = new Random();

        // ─── Step 1：隨機選一個主題 ──────────────────────────────────────────
        String topic      = TOPICS.get(random.nextInt(TOPICS.size()));
        String topicLabel = TOPIC_LABELS.getOrDefault(topic, topic);
        System.out.println("📌 選定主題：" + topicLabel + " (" + topic + ")");

        // ─── Step 2：從 dev.to 抓取該主題 top=7 文章 ─────────────────────────
        String devToUrl = "https://dev.to/api/articles?tag=" + topic + "&top=7&per_page=7";
        HttpRequest devToRequest = HttpRequest.newBuilder()
                .uri(URI.create(devToUrl))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> devToResponse = client.send(devToRequest,
                HttpResponse.BodyHandlers.ofString());

        String devToBody = devToResponse.body();
        System.out.println("📡 dev.to 回應狀態碼：" + devToResponse.statusCode());

        // 手動解析 JSON（不依賴第三方函式庫）
        // 將回傳的文章陣列切割成個別物件
        List<String> articles = splitJsonArray(devToBody);

        if (articles.isEmpty()) {
            throw new RuntimeException("❌ dev.to 回傳空文章列表，主題：" + topic);
        }

        // 隨機挑一篇
        String article = articles.get(random.nextInt(articles.size()));
        System.out.println("✅ 抓到 " + articles.size() + " 篇文章，隨機選出 1 篇");

        // 解析 title、url、tags
        String articleTitle = extractJsonString(article, "title");
        String articleUrl   = extractJsonString(article, "url");
        String tagsRaw      = extractTagArray(article); // e.g. ["java","api","tutorial"]

        System.out.println("📰 文章標題：" + articleTitle);
        System.out.println("🔗 文章網址：" + articleUrl);
        System.out.println("🏷  Tags    ：" + tagsRaw);

        // ─── Step 3：準備今天日期與時間 ──────────────────────────────────────
        String today     = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dateTime  = today + "T08:00:00.000+08:00"; // Taipei Time (UTC+8)

        // ─── Step 4：組合 Article_Title（主題 + Tags）────────────────────────
        String richTextContent = topicLabel + "  |  Tags：" + tagsRaw;

        // ─── Step 5：JSON Escaping ────────────────────────────────────────────
        String safeTitle   = escapeJson(articleTitle);
        String safeRich    = escapeJson(richTextContent);
        String safeUrl     = escapeJson(articleUrl);

        // ─── Step 6：組合 Notion API Request Body ────────────────────────────
        String notionBody = "{\n" +
            "  \"parent\": { \"database_id\": \"" + databaseId + "\" },\n" +
            "  \"icon\": { \"type\": \"emoji\", \"emoji\": \"📚\" },\n" +
            "  \"properties\": {\n" +
            "    \"Name\": {\n" +
            "      \"title\": [{ \"text\": { \"content\": \"" + safeTitle + "\" } }]\n" +
            "    },\n" +
            "    \"URL\": {\n" +
            "      \"url\": \"" + safeUrl + "\"\n" +
            "    },\n" +
            "    \"Date\": {\n" +
            "      \"date\": { \"start\": \"" + dateTime + "\" }\n" +
            "    },\n" +
            "    \"Article_Title\": {\n" +
            "      \"rich_text\": [{ \"text\": { \"content\": \"" + safeRich + "\" } }]\n" +
            "    }\n" +
            "  },\n" +
            "  \"children\": [\n" +
            // ── 第一行：@remind mention + 提醒文字 ──
            "    {\n" +
            "      \"object\": \"block\",\n" +
            "      \"type\": \"paragraph\",\n" +
            "      \"paragraph\": {\n" +
            "        \"rich_text\": [\n" +
            "          {\n" +
            "            \"type\": \"mention\",\n" +
            "            \"mention\": {\n" +
            "              \"type\": \"date\",\n" +
            "              \"date\": { \"start\": \"" + dateTime + "\" }\n" +
            "            },\n" +
            "            \"annotations\": { \"color\": \"blue_background\" }\n" +
            "          },\n" +
            "          {\n" +
            "            \"type\": \"text\",\n" +
            "            \"text\": { \"content\": \"  📚 起來練習 MIS 囉！\" }\n" +
            "          }\n" +
            "        ]\n" +
            "      }\n" +
            "    },\n" +
            // ── Divider ──
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            // ── 💡 我的見解 ──
            "    {\n" +
            "      \"object\": \"block\",\n" +
            "      \"type\": \"heading_3\",\n" +
            "      \"heading_3\": {\n" +
            "        \"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \"💡 我的見解\" } }]\n" +
            "      }\n" +
            "    },\n" +
            "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [] } },\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            // ── 📝 文章概要 ──
            "    {\n" +
            "      \"object\": \"block\",\n" +
            "      \"type\": \"heading_3\",\n" +
            "      \"heading_3\": {\n" +
            "        \"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \"📝 文章概要\" } }]\n" +
            "      }\n" +
            "    },\n" +
            "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [] } },\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            // ── ✨ 特別單字 ──
            "    {\n" +
            "      \"object\": \"block\",\n" +
            "      \"type\": \"heading_3\",\n" +
            "      \"heading_3\": {\n" +
            "        \"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \"✨ 特別單字\" } }]\n" +
            "      }\n" +
            "    },\n" +
            "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [] } },\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            // ── 📌 原文複製 ──
            "    {\n" +
            "      \"object\": \"block\",\n" +
            "      \"type\": \"heading_3\",\n" +
            "      \"heading_3\": {\n" +
            "        \"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \"📌 原文複製\" } }]\n" +
            "      }\n" +
            "    },\n" +
            "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [] } }\n" +
            "  ]\n" +
            "}";

        // ─── Step 7：呼叫 Notion API ──────────────────────────────────────────
        HttpRequest notionRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/pages"))
                .header("Authorization", "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(notionBody))
                .build();

        HttpResponse<String> notionResponse = client.send(notionRequest,
                HttpResponse.BodyHandlers.ofString());

        // ─── Debug 輸出 ───────────────────────────────────────────────────────
        System.out.println("\n══════════════════════════════════════");
        System.out.println("📬 Notion API 回應狀態碼：" + notionResponse.statusCode());
        System.out.println("📄 Notion API 回應內容：");
        System.out.println(notionResponse.body());
        System.out.println("══════════════════════════════════════");

        if (notionResponse.statusCode() == 200) {
            System.out.println("🎉 成功寫入 Notion！主題：" + topicLabel);
        } else {
            System.err.println("❌ 寫入失敗，請檢查上方回應內容");
        }
    }

    // ─── 工具函式 ─────────────────────────────────────────────────────────────

    /**
     * 將 JSON 陣列字串拆分為個別物件字串
     * 僅處理頂層陣列，不依賴第三方函式庫
     */
    private static List<String> splitJsonArray(String json) {
        List<String> result = new java.util.ArrayList<>();
        json = json.trim();
        if (!json.startsWith("[")) return result;

        int depth = 0;
        int start = -1;
        boolean inString = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) continue;
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start != -1) {
                    result.add(json.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return result;
    }

    /**
     * 從 JSON 物件字串中提取指定 key 的字串值
     */
    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx == -1) return "";
        int colonIdx = json.indexOf(":", keyIdx + search.length());
        if (colonIdx == -1) return "";

        int valueStart = json.indexOf("\"", colonIdx + 1);
        if (valueStart == -1) return "";

        StringBuilder sb = new StringBuilder();
        for (int i = valueStart + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                switch (next) {
                    case '"'  -> { sb.append('"');  i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'n'  -> { sb.append('\n'); i++; }
                    case 't'  -> { sb.append('\t'); i++; }
                    case 'r'  -> { sb.append('\r'); i++; }
                    default   -> { sb.append(c); }
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 從 JSON 物件中提取 tag_list 陣列，回傳逗號分隔字串
     */
    private static String extractTagArray(String json) {
        String search = "\"tag_list\"";
        int idx = json.indexOf(search);
        if (idx == -1) return "無標籤";

        int arrStart = json.indexOf("[", idx);
        int arrEnd   = json.indexOf("]", arrStart);
        if (arrStart == -1 || arrEnd == -1) return "無標籤";

        String arrContent = json.substring(arrStart + 1, arrEnd).trim();
        if (arrContent.isBlank()) return "無標籤";

        // 提取每個 "tag" 字串
        List<String> tags = new java.util.ArrayList<>();
        int pos = 0;
        while (pos < arrContent.length()) {
            int q1 = arrContent.indexOf('"', pos);
            if (q1 == -1) break;
            int q2 = arrContent.indexOf('"', q1 + 1);
            if (q2 == -1) break;
            tags.add(arrContent.substring(q1 + 1, q2));
            pos = q2 + 1;
        }
        return tags.isEmpty() ? "無標籤" : String.join(", ", tags);
    }

    /**
     * 對字串進行 JSON Escaping
     * 處理：雙引號、反斜線、換行、Tab、回車
     */
    private static String escapeJson(String input) {
        if (input == null) return "";
        return input
            .replace("\\", "\\\\")   // 反斜線必須第一個處理
            .replace("\"", "\\\"")   // 雙引號
            .replace("\n", "\\n")    // 換行
            .replace("\r", "\\r")    // 回車
            .replace("\t", "\\t");   // Tab
    }
}