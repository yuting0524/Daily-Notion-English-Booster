import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Random;

public class DevToToNotion {

    public static void main(String[] args) {
        try {
            // 1. 定義專業主題庫 (這些標籤在 dev.to 上通常產出高品質長文)
            String[] tagsPool = {
                "systemdesign", "database", "webdev", "devops", 
                "career", "performance", "security", "testing"
            };
            
            String selectedTag = tagsPool[new Random().nextInt(tagsPool.length)];
            HttpClient client = HttpClient.newHttpClient();

            // 2. MIS 邏輯：抓取該主題下「本週最強 (top=7)」的前 10 篇文章
            // 這樣可以過濾掉剛發布的隨手筆記
            HttpRequest devRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://dev.to/api/articles?tag=" + selectedTag + "&top=7&per_page=10"))
                .GET()
                .build();

            HttpResponse<String> devResponse = client.send(devRequest, HttpResponse.BodyHandlers.ofString());
            String body = devResponse.body();

            // 3. 從回傳的「菁英清單」中隨機挑選一筆，增加多樣性
            // (簡單用 split 拆分 JSON 陣列中的物件)
            String[] articles = body.split("\\{\"type_of\":\"article\""); 
            int randomIndex = new Random().nextInt(articles.length - 1) + 1;
            String pickedArticle = articles[randomIndex];

            // 4. 提取關鍵資訊
            String title = pickedArticle.split("\"title\":\"")[1].split("\",")[0];
            String url = pickedArticle.split("\"url\":\"")[1].split("\",")[0];
            String tags = pickedArticle.split("\"tags\":\"")[1].split("\",")[0];

            // 轉義處理
            String safeTitle = title.replace("\\", "\\\\").replace("\"", "\\\"");
            String todayDate = LocalDate.now().format(DateTimeFormatter.ISO_DATE);

            // 5. 組合 Notion Payload
            String notionToken = System.getenv("NOTION_TOKEN");
            String databaseId = System.getenv("DATABASE_ID");

            String jsonPayload = "{"
                + "\"parent\": { \"database_id\": \"" + databaseId + "\" },"
                + "\"properties\": {"
                + "    \"Name\": { \"title\": [ { \"text\": { \"content\": \"" + safeTitle + "\" } } ] },"
                + "    \"URL\": { \"url\": \"" + url + "\" },"
                + "    \"Date\": { \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } },"
                + "    \"Article_Title\": { \"rich_text\": [ { \"text\": { \"content\": \"主題: " + selectedTag.toUpperCase() + " | 標籤: " + tags + "\" } } ] }"
                + "},"
                + "\"children\": ["
                + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ "
                + "    { \"type\": \"mention\", \"mention\": { \"type\": \"date\", \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } } },"
                + "    { \"text\": { \"content\": \" 🚀 今日 MIS 練習：從 " + selectedTag + " 菁英榜中選出\" } } "
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

            // 6. 發送
            HttpRequest notionRequest = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/pages"))
                .header("Authorization", "Bearer " + notionToken)
                .header("Notion-Version", "2022-06-28")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            client.send(notionRequest, HttpResponse.BodyHandlers.ofString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}