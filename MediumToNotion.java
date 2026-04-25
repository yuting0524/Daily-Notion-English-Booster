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
            // 1. 必中標籤清單
            String[] tags = {
                "computer-science", "programming", "technology", 
                "software-development", "coding", "artificial-intelligence", 
                "software-engineering", "machine-learning", "algorithms", "ai"
            };
            
            // 2. 隨機抽選與標題處理
            String selectedTag = tags[new Random().nextInt(tags.length)];
            String articleTitle = "Daily Tech Study: [" + selectedTag.toUpperCase() + "]";
            String articleUrl = "https://medium.com/tag/" + selectedTag.toLowerCase();
            String todayDate = LocalDate.now().toString(); // 這裡只拿 2026-04-25

            // 3. 拼裝 JSON (確保時間格式 T08:00 只出現一次)
            String jsonPayload = "{"
    + "\"parent\": { \"database_id\": \"" + DATABASE_ID + "\" },"
    + "\"properties\": {"
    + "    \"Name\": { \"title\": [ { \"text\": { \"content\": \"" + articleTitle + "\" } } ] },"
    + "    \"URL\": { \"url\": \"" + articleUrl + "\" },"
    + "    \"Date\": { \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } },"
    + "    \"Article_Title\": { \"rich_text\": [ { \"text\": { \"content\": \"(在此手動輸入文章名稱)\" } } ] }"
    + "},"
    + "\"children\": ["
    // --- 區塊 0：強制鬧鐘標籤 (讓手機 08:00 會叮一聲) ---
    + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ "
    + "    { \"type\": \"mention\", \"mention\": { \"type\": \"date\", \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } } },"
    + "    { \"type\": \"text\", \"text\": { \"content\": \" 📚 起來讀英文囉！\" } }"
    + "  ] } },"
    + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"

    // --- 區塊 1：我的見解 ---
    + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"💡 我的見解\" } } ] } },"
    + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } },"
    + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"

    // --- 區塊 2：文章概要 ---
    + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"📝 文章概要\" } } ] } },"
    + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } },"
    + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"

    // --- 區塊 3：特別單字 ---
    + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"✨ 特別單字\" } } ] } },"
    + "  { \"object\": \"block\", \"type\": \"bulleted_list_item\", \"bulleted_list_item\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } },"
    + "  { \"object\": \"block\", \"type\": \"divider\", \"divider\": {} },"

    // --- 區塊 4：原文複製 ---
    + "  { \"object\": \"block\", \"type\": \"heading_3\", \"heading_3\": { \"rich_text\": [ { \"text\": { \"content\": \"📌 原文複製\" } } ] } },"
    + "  { \"object\": \"block\", \"type\": \"quote\", \"quote\": { \"rich_text\": [ { \"text\": { \"content\": \" \" } } ] } }"
    + "]"
    + "}";

            // 4. 發射到 Notion
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
        
        int responseCode = conn.getResponseCode();
        if (responseCode != 200 && responseCode != 201) {
            throw new RuntimeException("HTTP 錯誤代碼: " + responseCode);
        }
    }
}