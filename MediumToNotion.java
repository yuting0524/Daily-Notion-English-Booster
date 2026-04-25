import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class MediumToNotion {

    private static final String NOTION_TOKEN = System.getenv("NOTION_TOKEN");
    private static final String DATABASE_ID = System.getenv("DATABASE_ID");

    public static void main(String[] args) {
        try {
            String todayDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

            // 1. 抓取資料
            String devToUrl = "https://dev.to/api/articles?tag=algorithms&per_page=1";
            System.out.println("正在從 Dev.to 抓取文章...");
            String response = fetchRawData(devToUrl);
            
            // 2. 強化版字串解析 (處理空格與引號)
            String articleTitle = extractValue(response, "title");
            String articleUrl = extractValue(response, "url");
            
            System.out.println("抓到標題: " + articleTitle);
            System.out.println("抓到網址: " + articleUrl);

            // 3. 組合 Payload (保留妳要的四大區塊)
            String jsonPayload = "{"
                + "\"parent\": { \"database_id\": \"" + DATABASE_ID + "\" },"
                + "\"properties\": {"
                + "    \"Name\": { \"title\": [ { \"text\": { \"content\": \"" + articleTitle + "\" } } ] },"
                + "    \"URL\": { \"url\": \"" + articleUrl + "\" },"
                + "    \"Date\": { \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } },"
                + "    \"Article_Title\": { \"rich_text\": [ { \"text\": { \"content\": \"(Dev.to 自動派送)\" } } ] }"
                + "},"
                + "\"children\": ["
                + "  { \"object\": \"block\", \"type\": \"paragraph\", \"paragraph\": { \"rich_text\": [ "
                + "    { \"type\": \"mention\", \"mention\": { \"type\": \"date\", \"date\": { \"start\": \"" + todayDate + "T08:00:00.000+08:00\" } } },"
                + "    { \"type\": \"text\", \"text\": { \"content\": \" 📚 起來讀英文囉！\" } }"
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

            sendToNotion(jsonPayload);

        } catch (Exception e) {
            System.err.println("發生錯誤！詳細訊息：");
            e.printStackTrace();
        }
    }

    private static String fetchRawData(String apiUrl) throws Exception {
        URL url = new URL(apiUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) response.append(line);
            return response.toString();
        }
    }

    // 強化版的解析器：更靈活地尋找 JSON 的值
    private static String extractValue(String json, String key) {
        try {
            int keyIndex = json.indexOf("\"" + key + "\"");
            int colonIndex = json.indexOf(":", keyIndex);
            int quoteStart = json.indexOf("\"", colonIndex);
            int quoteEnd = json.indexOf("\"", quoteStart + 1);
            return json.substring(quoteStart + 1, quoteEnd);
        } catch (Exception e) {
            return "解析失敗";
        }
    }

    private static void sendToNotion(String payload) throws Exception {
        URL url = new URL("https://api.notion.com/v1/pages");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + NOTION_TOKEN);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Notion-Version", "2022-06-28");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload.getBytes("utf-8"));
        }
        System.out.println("Notion 回傳狀態碼: " + conn.getResponseCode());
    }
}