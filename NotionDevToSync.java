import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    // ─── 主題清單（已對照 dev.to 實際 tag slug 驗證）────────────────────────────
    // 驗證方式：https://dev.to/api/articles?tag=TAGNAME&top=7
    private static final List<String> TOPICS = List.of(
        // 🛠 Hard Tech
        "javascript",
        "python",
        "webdev",
        "programming",
        "ai",
        "machinelearning",
        "computerscience",
        "architecture",
        "opensource",
        "beginners",
        // 📊 MIS & Management
        "productivity",
        "startup",
        "product",
        "agile",
        // 🚀 Soft Skills
        "career",
        "learning",
        "tutorial",
        "devops",
        "discuss"
    );

    // ─── 中文標籤對照（用於 Article_Title 顯示）──────────────────────────────
    private static final Map<String, String> TOPIC_LABELS = new HashMap<>();
    static {
        TOPIC_LABELS.put("javascript",      "💻 JavaScript");
        TOPIC_LABELS.put("python",          "🐍 Python");
        TOPIC_LABELS.put("webdev",          "🌐 Web Development");
        TOPIC_LABELS.put("programming",     "🧑‍💻 Programming");
        TOPIC_LABELS.put("ai",              "🤖 AI / ML");
        TOPIC_LABELS.put("machinelearning", "🧠 Machine Learning");
        TOPIC_LABELS.put("computerscience", "🖥 Computer Science");
        TOPIC_LABELS.put("architecture",    "🏗 System Design");
        TOPIC_LABELS.put("opensource",      "📦 Open Source");
        TOPIC_LABELS.put("beginners",       "🌱 Beginners");
        TOPIC_LABELS.put("productivity",    "⚡ Productivity");
        TOPIC_LABELS.put("startup",         "🚀 Startup / Entrepreneurship");
        TOPIC_LABELS.put("product",         "📦 Product Management");
        TOPIC_LABELS.put("agile",           "📅 Agile / Project Management");
        TOPIC_LABELS.put("career",          "🎯 Career Advice");
        TOPIC_LABELS.put("learning",        "📚 Learning");
        TOPIC_LABELS.put("tutorial",        "🎓 Tutorial");
        TOPIC_LABELS.put("devops",          "⚙️ DevOps");
        TOPIC_LABELS.put("discuss",         "💬 Tech Discussion");
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

        // ─── Step 1：隨機選一個主題，並建立可重新賦值的變數 ─────────────────
        List<String> shuffled = new ArrayList<>(TOPICS);
        java.util.Collections.shuffle(shuffled, random);

        String topic      = shuffled.get(0);
        String topicLabel = TOPIC_LABELS.getOrDefault(topic, topic);
        System.out.println("📌 選定主題：" + topicLabel + " (" + topic + ")");

        // ─── Step 2：從 dev.to 抓取文章，若空則自動換主題（最多試 5 次）──────
        List<String> articles = new ArrayList<>();
        String devToBody = "";
        int tried = 0;

        for (String candidate : shuffled) {
            tried++;
            topic      = candidate;
            topicLabel = TOPIC_LABELS.getOrDefault(topic, topic);

            String devToUrl = "https://dev.to/api/articles?tag=" + topic + "&top=7&per_page=7";
            HttpRequest devToRequest = HttpRequest.newBuilder()
                    .uri(URI.create(devToUrl))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> devToResponse = client.send(devToRequest,
                    HttpResponse.BodyHandlers.ofString());

            devToBody = devToResponse.body();
            System.out.println("📡 dev.to 狀態碼：" + devToResponse.statusCode()
                    + "  主題：" + topic + "（第 " + tried + " 次）");

            articles = splitJsonArray(devToBody);

            if (!articles.isEmpty()) {
                System.out.println("✅ 找到 " + articles.size() + " 篇文章，使用主題：" + topicLabel);
                break;
            } else {
                System.out.println("⚠️  該主題無文章，換下一個...");
            }

            if (tried >= 5) break;
        }

        if (articles.isEmpty()) {
            throw new RuntimeException("❌ 嘗試 " + tried + " 個主題後仍無文章，請檢查 dev.to API 或 tag 清單");
        }

        // ─── Step 3：隨機挑一篇文章 ──────────────────────────────────────────
        String article = articles.get(random.nextInt(articles.size()));

        String articleTitle = extractJsonString(article, "title");
        String articleUrl   = extractJsonString(article, "url");
        String tagsRaw      = extractTagArray(article);

        System.out.println("📰 文章標題：" + articleTitle);
        System.out.println("🔗 文章網址：" + articleUrl);
        System.out.println("🏷  Tags    ：" + tagsRaw);

        // ─── Step 4：準備今天日期與時間 ──────────────────────────────────────
        String today    = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String dateTime = today + "T08:00:00.000+08:00";

        // ─── Step 5：組合 Article_Title（主題 + Tags）────────────────────────
        String richTextContent = topicLabel + "  |  Tags：" + tagsRaw;

        // ─── Step 6：JSON Escaping ────────────────────────────────────────────
        String safeTitle = escapeJson(articleTitle);
        String safeRich  = escapeJson(richTextContent);
        String safeUrl   = escapeJson(articleUrl);

        // ─── Step 7：組合 Notion API Request Body ────────────────────────────
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
            // @remind mention block
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
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            // 💡 我的見解
            "    {\n" +
            "      \"object\": \"block\",\n" +
            "      \"type\": \"heading_3\",\n" +
            "      \"heading_3\": {\n" +
            "        \"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \"💡 我的見解\" } }]\n" +
            "      }\n" +
            "    },\n" +
            "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [] } },\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            // 📝 文章概要
            "    {\n" +
            "      \"object\": \"block\",\n" +
            "      \"type\": \"heading_3\",\n" +
            "      \"heading_3\": {\n" +
            "        \"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \"📝 文章概要\" } }]\n" +
            "      }\n" +
            "    },\n" +
            "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [] } },\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            // ✨ 特別單字
            "    {\n" +
            "      \"object\": \"block\",\n" +
            "      \"type\": \"heading_3\",\n" +
            "      \"heading_3\": {\n" +
            "        \"rich_text\": [{ \"type\": \"text\", \"text\": { \"content\": \"✨ 特別單字\" } }]\n" +
            "      }\n" +
            "    },\n" +
            "    { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [] } },\n" +
            "    { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },\n" +
            // 📌 原文複製
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

        // ─── Step 8：呼叫 Notion API ──────────────────────────────────────────
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

    /** 將 JSON 陣列字串拆分為個別物件字串 */
    private static List<String> splitJsonArray(String json) {
        List<String> result = new ArrayList<>();
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

    /** 從 JSON 物件字串中提取指定 key 的字串值 */
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
                    default   -> sb.append(c);
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 從 JSON 物件中提取 tag_list 陣列，回傳逗號分隔字串 */
    private static String extractTagArray(String json) {
        String search = "\"tag_list\"";
        int idx = json.indexOf(search);
        if (idx == -1) return "無標籤";

        int arrStart = json.indexOf("[", idx);
        int arrEnd   = json.indexOf("]", arrStart);
        if (arrStart == -1 || arrEnd == -1) return "無標籤";

        String arrContent = json.substring(arrStart + 1, arrEnd).trim();
        if (arrContent.isBlank()) return "無標籤";

        List<String> tags = new ArrayList<>();
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

    /** 對字串進行 JSON Escaping */
    private static String escapeJson(String input) {
        if (input == null) return "";
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}