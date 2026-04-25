import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class MediumToNotion {
    public static void main(String[] args) {
        try {
            // 1. 取得環境變數 (GitHub Secrets)
            String notionToken = System.getenv("NOTION_TOKEN");
            String databaseId = System.getenv("DATABASE_ID");

            // 這裡假設妳已經有抓到 articleTitle 和 articleUrl
            // 測試用，實際跑的時候會由妳原本的抓取邏輯提供
            String articleTitle = "How to Study Computer Science"; 
            String articleUrl = "https://medium.com/example";
            String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

            // --- 重點：處理特殊字元，防止 JSON 崩潰 ---
            String safeTitle = articleTitle.replace("\\", "\\\\").replace("\"", "\\\"");
            String safeUrl = articleUrl.replace("\\", "\\\\").replace("\"", "\\\"");

            // 2. 組合 JSON Payload (properties + children)
            String jsonPayload = "{"
                + "\"parent\": { \"database_id\": \"" + databaseId + "\" },"
                + "\"properties\": {"
                + "    \"Name\": { \"title\": [ { \"text\": { \"content\": \"" + safeTitle + "\" } } ] },"
                + "    \"URL\": { \"url\": \"" + safeUrl + "\" },"
                + "    \"Date\": { \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } },"
                + "    \"Article_Title\": { \"rich_text\": [ { \"text\": { \"content\": \"(在此手動輸入文章名稱)\" } } ] }"
                + "},"
                + "\"children\": ["
                // 強制鬧鐘：在頁面內文加入 @mention date
                + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ "
                + "    { \"type\": \"mention\", \"mention\": { \"type\": \"date\", \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } } },"
                + "    { \"type\": \"text\", \"text\": { \"content\": \" 📚 起來讀英文囉！\" } }"
                + "  ] } },"
                + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"
                // 區塊 1：我的見解
                + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"💡 我的見解\" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"
                // 區塊 2：文章概要
                + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"📝 文章概要\" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"
                // 區塊 3：特別單字
                + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"✨ 特別單字\" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"bulleted_list_item\", \"bulleted_list_item\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"
                // 區塊 4：原文複製
                + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"📌 原文複製\" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"quote\", \"quote\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } }"
                + "]"
                + "}";

            // 3. 發送請求
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/pages"))
                .header("Authorization", "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            // 4. 印出回應 (這一步在 GitHub Logs 裡很重要，可以看到錯誤訊息)
            System.out.println("Status Code: " + response.statusCode());
            System.out.println("Response: " + response.body());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}