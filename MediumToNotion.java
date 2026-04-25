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

            // 1. 從 dev.to 抓取最新的熱門文章 (這裡設為抓取 'management' 標籤相關的文章)
            HttpRequest devToRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://dev.to/api/articles?tag=management&top=1"))
                .GET()
                .build();

            HttpResponse<String> devToResponse = client.send(devToRequest, HttpResponse.BodyHandlers.ofString());
            String responseBody = devToResponse.body();

            // 2. 簡易解析 dev.to 回傳的資料 (這裡建議用原本妳熟悉的抓取方式，以下為簡化邏輯)
            // 抓出標題、網址與標籤 (tags)
            String articleTitle = responseBody.split("\"title\":\"")[1].split("\",")[0];
            String articleUrl = responseBody.split("\"url\":\"")[1].split("\",")[0];
            String tags = responseBody.split("\"tags\":\"")[1].split("\",")[0];

            // 處理特殊字元轉義 (預防 JSON 崩潰)
            String safeTitle = articleTitle.replace("\"", "\\\"");
            String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

            // 3. 組合發送給 Notion 的 JSON
            String notionToken = System.getenv("NOTION_TOKEN");
            String databaseId = System.getenv("DATABASE_ID");

            String jsonPayload = "{"
                + "\"parent\": { \"database_id\": \"" + databaseId + "\" },"
                + "\"properties\": {"
                + "    \"Name\": { \"title\": [ { \"text\": { \"content\": \"" + safeTitle + "\" } } ] },"
                + "    \"URL\": { \"url\": \"" + articleUrl + "\" },"
                + "    \"Date\": { \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } },"
                + "    \"Article_Title\": { \"rich_text\": [ { \"text\": { \"content\": \"Tags: " + tags + "\" } } ] }"
                + "},"
                + "\"children\": ["
                + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ "
                + "    { \"type\": \"mention\", \"mention\": { \"type\": \"date\", \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } } },"
                + "    { \"text\": { \"content\": \" 📚 起來練習 MIS 囉！文章標籤：" + tags + "\" } } "
                + "  ] } },"
                + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"
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

            // 4. 發送至 Notion API
            HttpRequest notionRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/pages"))
                .header("Authorization", "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = client.send(notionRequest, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response.statusCode());
            System.out.println("Body: " + response.body());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}