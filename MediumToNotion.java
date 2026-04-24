import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.Random;

public class MediumToNotion {

    private static final String NOTION_TOKEN = System.getenv("NOTION_TOKEN");
    private static final String DATABASE_ID = System.getenv("DATABASE_ID");

    public static void main(String[] args) {
        try {
            // ✅ 這裡完全採用妳剛剛看到的 10 個標籤，並處理成 URL 格式
            String[] tags = {
                "computer-science", 
                "programming", 
                "technology", 
                "software-development", 
                "coding", 
                "artificial-intelligence", 
                "software-engineering", 
                "machine-learning", 
                "algorithms", 
                "ai"
            };
            
            // 隨機抽一個
            String selectedTag = tags[new Random().nextInt(tags.length)];
            
            String articleTitle = "Daily Tech Study: [" + selectedTag.toUpperCase() + "]";
            String articleUrl = "https://medium.com/tag/" + selectedTag.toLowerCase();
            String todayDate = LocalDate.now().toString(); 

            String jsonPayload = "{"
                + "\"parent\": { \"database_id\": \"" + DATABASE_ID + "\" },"
                + "\"properties\": {"
                + "    \"Name\": { \"title\": [ { \"text\": { \"content\": \"" + articleTitle + "\" } } ] },"
                + "    \"URL\": { \"url\": \"" + articleUrl + "\" },"
                + "    \"Date\": { \"date\": { \"start\": \"" + todayDate + "\" } }"
                + "},"
                + "\"children\": ["
                + "  { \"object\": \"block\", \"type\": \"heading_2\", \"heading_2\": { \"rich_text\": [ { \"text\": { \"content\": \"📝 今日練習摘要\" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"
                + "  { \"object\": \"block\", \"type\": \"bulleted_list_item\", \"bulleted_list_item\": { \"rich_text\": [ { \"text\": { \"content\": \"關鍵單字 1: \" } } ] } },"
                + "  { \"object\": \"block\", \"type\": \"bulleted_list_item\", \"bulleted_list_item\": { \"rich_text\": [ { \"text\": { \"content\": \"關鍵單字 2: \" } } ] } }"
                + "]"
                + "}";

            sendToNotion(jsonPayload);
            System.out.println("✅ 成功！今日主題：" + selectedTag);

        } catch (Exception e) {
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
        if (conn.getResponseCode() != 200 && conn.getResponseCode() != 201) {
            throw new RuntimeException("Error: " + conn.getResponseCode());
        }
    }
}