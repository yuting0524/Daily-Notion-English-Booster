import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.Random;

public class MediumToNotion {

    // 這是資安重點：從 GitHub Secrets (環境變數) 讀取密鑰，不寫死在程式碼裡
    private static final String NOTION_TOKEN = System.getenv("NOTION_TOKEN");
    private static final String DATABASE_ID = System.getenv("DATABASE_ID");

    public static void main(String[] args) {
        try {
            // 1. 定義妳感興趣的資管/考研主題標籤
            String[] tags = {"Management-Information-Systems", "Algorithms", "Artificial-Intelligence", "Cybersecurity", "Software-Engineering"};
            String selectedTag = tags[new Random().nextInt(tags.length)];
            
            String articleTitle = "Daily Tech Study: [" + selectedTag + "]";
            String articleUrl = "https://medium.com/tag/" + selectedTag.toLowerCase();
            String todayDate = LocalDate.now().toString(); 

            // 2. 建立 JSON 資料主體 (包含 Name, URL, Date 屬性，以及頁面內的練習區區塊)
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

            // 3. 執行傳送邏輯
            sendToNotion(jsonPayload);
            System.out.println("機器人執行成功！主題是: " + selectedTag);

        } catch (Exception e) {
            System.err.println("執行失敗，請檢查 Token 與 Database ID 是否設定正確。");
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