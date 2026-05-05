# 🚀 Automated Technical Literature Retrieval System (ATLR)

![Java Version](https://img.shields.io/badge/Java-17-orange.svg)
![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

系統旨在每日定時檢索學術界（arXiv）與技術圈（Hacker News）的高品質文獻，並透過 Notion API 進行結構化存儲與即時通知，建立系統化的知識管理流程。

---

## 🛠️ 核心技術特性

### 1. 多層次容錯檢索機制 (Resilient Data Retrieval)
系統具備自動化故障轉移（Failover）邏輯，確保資訊獲取的穩定性：
* **主要來源 (Primary)**：透過 arXiv API 檢索特定電腦科學（CS）與統計學（Stat）領域之最新論文。
* **備援來源 (Fallback)**：當主要來源回應異常時，系統將自動依序切換至 **Hacker News** 與 **Wikipedia REST API**，確保每日數據流不中斷。

### 2. 推播通知優化 (Notification Optimization)
針對 Notion API 對於日期提醒（Date Reminders）在行動裝置上推播不穩定的技術限制，本系統導入了 **User Mention 觸發機制**：
* **技術實現**：在 JSON Payload 中動態注入 `mention user` 區塊，直接標註使用者 UUID。
* **執行效果**：GitHub Actions 執行完畢後，能確保使用者在手機端即時收到系統級推播通知。

### 3. 高安全性憑證管理 (Secrets Management)
遵循資安最佳實踐，所有敏感憑證均透過環境變數管理：
* **Zero Hardcoding**：不將 API Tokens 或私密 ID 寫入原始碼。
* **Encrypted Storage**：利用 GitHub Secrets 存儲加密資訊，並在執行期（Runtime）透過 YAML 映射至環境變數。

---

## 📊 資料庫模式設計 (Database Schema)

| 欄位名稱 (Property) | 資料類型 | 功能描述 |
| :--- | :--- | :--- |
| **Name** | Title | 文獻原始標題 |
| **URL** | URL | 原始資料源導向連結 |
| **Date** | Date | 系統處理時間與定時提醒戳記 |
| **Article_Title** | Rich Text | 類別標籤與作者資訊之元數據 |

### 資料庫範本連結 (Template Link)
為了確保系統穩定運作，您可以參考或複製以下預先配置好的 Notion 資料庫範本：
[點擊此處查看 Notion 資料庫範本](https://www.notion.so/fae3816f7d3b83718e6101eb312f4c77?v=ec13816f7d3b835695168843e84fed36&source=copy_link)
*注意：複製後請記得在該資料庫的右上角「...」→「Connections」中加入您自己的 Integration 授權。*
*實際介面可至assets資料夾查看*


## 🚀 部署流程 (Deployment Guide)

1.  **Fork 專案**：將本倉庫複製至個人帳號下以啟用個人化排程。
2.  **Notion 整合設定**：
    * 於 [Notion Developers](https://www.notion.so/my-integrations) 建立 Integration。
    * 在目標資料庫開啟 **Connections** 授權。
3.  **配置 GitHub Secrets**：
    於 Settings > Secrets and variables > Actions 中新增：
    * `NOTION_TOKEN`：您的整合金鑰。
    * `DATABASE_ID`：目標資料庫 ID。
    * `NOTION_USER_ID`：使用者 UUID。
4.  **啟動自動化**：
    * 系統預設每日台北時間 (UTC+8) **08:00** 自動執行。
    * 亦可透過 Actions 頁面手動點擊 **Run workflow**。

---

## 💻 技術堆棧 (Technical Stack)

* **Runtime Environment**: OpenJDK 17
* **Networking**: Native Java `HttpClient`
* **CI/CD Orchestration**: GitHub Actions
* **Data Interchange**: JSON / RESTful API

---

## 📜 許可聲明 (License)
本專案採用 **MIT 許可證** 開源。歡迎任何形式的貢獻、Fork 或功能建議。