import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * 查詢 Notion User ID 並直接發送測試推播的工具
 */
public class TestNotionMention {
    public static void main(String[] args) throws Exception {
        // ⚠️ 請在這裡填入你的 Token 與測試用 Page ID
        String token = "請替換成你的_NOTION_TOKEN";
        String pageId = "請替換成你的_PAGE_ID";

        if (token.contains("請替換") || pageId.contains("請替換")) {
            System.out.println("❌ 請先在程式碼中填寫你的 NOTION_TOKEN 與 PAGE_ID！");
            return;
        }

        // ==========================================
        // 1. 取得使用者列表並找出 User ID
        // ==========================================
        System.out.println("🔍 正在查詢你的 User ID...");
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/users"))
                .header("Authorization", "Bearer " + token)
                .header("Notion-Version", "2022-06-28")
                .GET()
                .build();

        HttpResponse<String> getRes = HttpClient.newHttpClient().send(getReq, HttpResponse.BodyHandlers.ofString());

        String targetUserId = null;
        String[] users = getRes.body().split("\"object\":\"user\"");

        for (String user : users) {
            if (user.contains("\"type\":\"person\"")) {
                String id = extractValue(user, "id");
                String name = extractValue(user, "name");
                System.out.println("👤 找到使用者: " + name + " (ID: " + id + ")");

                // 抓取第一個真人使用者來進行推播測試
                if (targetUserId == null) {
                    targetUserId = id;
                }
            }
        }

        if (targetUserId == null) {
            System.out.println("❌ 找不到真人使用者，無法進行推播測試。");
            return;
        }

        // ==========================================
        // 2. 利用抓到的 User ID 發送測試推播
        // ==========================================
        System.out.println("\n🚀 準備發送測試推播給 ID: " + targetUserId);

        String jsonBody = "{\n" +
                "  \"children\": [\n" +
                "    {\n" +
                "      \"object\": \"block\",\n" +
                "      \"type\": \"paragraph\",\n" +
                "      \"paragraph\": {\n" +
                "        \"rich_text\": [\n" +
                "          { \"type\": \"text\", \"text\": { \"content\": \"🔔 嗨！這是一條 Java 程式發送的推播測試：\" } },\n" +
                "          { \"type\": \"mention\", \"mention\": { \"type\": \"user\", \"user\": { \"id\": \""
                + targetUserId + "\" } } }\n" +
                "        ]\n" +
                "      }\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        HttpRequest patchReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.notion.com/v1/blocks/" + pageId + "/children"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Notion-Version", "2022-06-28")
                // 使用 PATCH 方法附加到頁面最下方
                .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> patchRes = HttpClient.newHttpClient().send(patchReq, HttpResponse.BodyHandlers.ofString());

        if (patchRes.statusCode() == 200) {
            System.out.println("✅ 測試發送成功！請立刻檢查你的手機或 Notion 電腦版的 Inbox！");
        } else {
            System.out.println("❌ 測試發送失敗，狀態碼：" + patchRes.statusCode());
            System.out.println(patchRes.body());
        }
    }

    // 簡易的 JSON 字串提取工具
    private static String extractValue(String jsonPart, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = jsonPart.indexOf(searchKey);
        if (start == -1)
            return "未知";
        start += searchKey.length();
        int end = jsonPart.indexOf("\"", start);
        return jsonPart.substring(start, end);
    }
}