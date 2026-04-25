import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DevToToNotion {
    public static void main(String[] args) {
        try {
            HttpClient client = HttpClient.newHttpClient();

            // 1. MIS 練習：從 API 自動抓取技術文章 (不手動找，讓程式找)
            // 我們抓 'management' 或 'productivity' 標籤的文章來練習
            HttpRequest devRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://dev.to/api/articles?tag=management&top=1"))
                .GET()
                .build();

            HttpResponse<String> devResponse = client.send(devRequest, HttpResponse.BodyHandlers.ofString());
            String body = devResponse.body();

            // 2. 簡易解析 (MIS 重點：提取關鍵標籤)
            // 這裡從 JSON 陣列中抓出第一篇文章的標題、網址與標籤
            String articleTitle = body.split("\"title\":\"")[1].split("\",")[0];
            String articleUrl = body.split("\"url\":\"")[1].split("\",")[0];
            String tags = body.split("\"tags\":\"")[1].split("\",")[0];

            // 3. 處理特殊字元 (轉義)
            String safeTitle = articleTitle.replace("\\", "\\\\").replace("\"", "\\\"");
            String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

            // 4. 發送至 Notion
            String notionToken = System.getenv("NOTION_TOKEN");
            String databaseId = System.getenv("DATABASE_ID");

            String jsonPayload = "{"
                + "\"parent\": { \"database_id\": \"" + databaseId + "\" },"
                + "\"properties\": {"
                + "    \"Name\": { \"title\": [ { \"text\": { \"content\": \"" + safeTitle + "\" } } ] },"
                + "    \"URL\": { \"url\": \"" + articleUrl + "\" },"
                + "    \"Date\": { \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } },"
                + "    \"Article_Title\": { \"rich_text\": [ { \"text\": { \"content\": \"標籤: " + tags + "\" } } ] }"
                + "},"
                + "\"children\": ["
                // 強制提醒鬧鐘
                + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ "
                + "    { \"type\": \"mention\", \"mention\": { \"type\": \"date\", \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } } },"
                + "    { \"text\": { \"content\": \" 📚 起來練習 MIS 囉！今天的標籤是：" + tags + "\" } } "
                + "  ] } },"
                + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"
                // 妳要求的四個練習區塊
                + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"💡 我的見解\" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"
                + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"📝 文章概要\" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"
                + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"✨ 特別單字\" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"bulleted_list_item\", \"bulleted_list_item\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"
                + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"📌 原文複製\" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"quote\", \"quote\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } }"
                + "]"
                + "}";

            HttpRequest notionRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/pages"))
                .header("Authorization", "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = client.send(notionRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("Notion 回應碼: " + response.statusCode());
            System.out.println("回應內容: " + response.body());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}