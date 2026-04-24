import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.Random;

public class MediumToNotion {

    // 從 GitHub Secrets 讀取密鑰
    private static final String NOTION_TOKEN = System.getenv("NOTION_TOKEN");
    private static final String DATABASE_ID = System.getenv("DATABASE_ID");

    public static void main(String[] args) {
        try {
            // 1. 妳在 Medium 看到的熱門技術標籤（已轉換為 URL 格式）
            String[] tags = {
                "computer-science", "programming", "technology", 
                "software-development", "coding", "artificial-intelligence", 
                "software-engineering", "machine-learning", "algorithms", "ai"
            };
            
            // 隨機抽一個標籤
            String selectedTag = tags[new Random().nextInt(tags.length)];
            
            // 2. 準備要送到 Notion 的內容
            String articleTitle = "Daily Tech Study: [" + selectedTag.toUpperCase() + "]";
            // 這裡使用 /tag/ 路徑，確保能連到 Medium 的分類頁
            String articleUrl = "https://medium.com/tag/" + selectedTag.toLowerCase();
            String todayDate = LocalDate.now().toString(); 

            // 3. 建立符合 Notion API 規範的 JSON (包含內容區塊)
            String jsonPayload = "{"
                + "\"parent\": { \"database_id\": \"" + DATABASE_ID + "\" },"
                + "\"properties\": {"
                + "    \"Name\": { \"title\": [ { \"text\": { \"content\": \"" + articleTitle + "\" } } ] },"
                + "    \"URL\": { \"url\": \"" + articleUrl + "\" },"
                + "    \"Date\": { \"date\": { \"start\": \"" + todayDate + "\" } }"
                + "},"
                + "\"children\": ["
                + "  { \"object\": \"block\", \"type\": \"heading_2\", \"heading_2\": { \"rich_text\": [ { \"text\": { \"content\": \"📝 今日練習摘要\" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ { \"text\": { \"content\": \"(在此輸入妳對文章的理解與筆記...)\" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"
                + "  { \"object\": \"block\", \"type\": \"bulleted_list_item\", \"bulleted_list_item\": { \"rich_text\": [ { \"text\": { \"content\": \"關鍵單字 1: \" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"bulleted_list_item\", \"bulleted_list_item\": { \"rich_text\": [ { \"text\": { \"content\": \"關鍵單字 2: \" } } ] } }"
                + "]"
                + "}";

            sendToNotion(jsonPayload);
            System.out.println("✅ 機器人執行成功！今日主題是: " + selectedTag);

        } catch (Exception e) {
            System.err.println("❌ 執行失敗，請檢查 Token 與 ID。");
            e.printStackTrace();
        }
    }

    private static void sendToNotion(String jsonBody) throws Exception {
        URL url = new URL("https://api.notion.com/v1/pages");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + NOTION_TOKEN);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Notion-Version", "2022-06-28");
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("utf-8"));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200 && responseCode != 201) {
            throw new RuntimeException("HTTP 錯誤代碼: " + responseCode);
        }
    }
}