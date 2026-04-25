# 系統名稱：Automated Technical Literature Retrieval System (Notion Integration)

本專案為一套基於 MIS（管理資訊系統）邏輯設計的自動化資訊處理系統。透過 GitHub Actions 驅動 Java 程式執行，每日定時檢索學術與技術領域的高品質文獻，並透過 Notion API 進行資料持久化存儲，旨在建立系統化的每日閱讀與分析習慣。

## 核心功能

1. **多來源自動檢索 (Multi-Source Retrieval)**
   * **主要來源：arXiv API** — 針對 8 個特定學術領域進行隨機抽樣，獲取最新發布之學術論文。
   * **次要來源 (Fallback 1)：Hacker News API** — 檢索科技、創業與計算機科學領域之高權重熱門文章。
   * **保底來源 (Fallback 2)：Wikipedia REST API** — 檢索每日精選內容，確保系統執行不因外部來源失效而中斷。

2. **自動容錯機制 (Fault Tolerance)**
   * 導入 Fallback 策略，當主要來源 API 回應異常時，系統將自動依序切換至備援來源。

3. **排程自動化 (Scheduling)**
   * 設定台北時間 (UTC+8) 08:00 定時執行，並支援 GitHub Workflow Dispatch 手動觸發機制。

## arXiv 檢索領域配置

| 分類 ID | 技術領域 (Subject) |
| :--- | :--- |
| cs.AI | Artificial Intelligence |
| cs.LG | Machine Learning |
| cs.SE | Software Engineering |
| cs.IR | Information Retrieval |
| cs.CY | Computers and Society |
| cs.HC | Human-Computer Interaction |
| econ.GN | General Economics |
| stat.ML | Statistics / Machine Learning |

## 系統架構與資料庫結構 (Database Schema)

在 Notion 資料庫中需預先配置以下欄位以完成資料對接：

| 欄位名稱 | 資料類型 | 描述內容 |
| :--- | :--- | :--- |
| Name | Title | 文章或論文標題 |
| URL | URL | 原始文獻連結 |
| Date | Date | 執行當天 08:00 (包含系統提醒) |
| Article_Title | Rich Text | 來源分類與作者資訊標籤 |

### 資料庫範本連結 (Template Link)
為了確保系統穩定運作，您可以參考或複製以下預先配置好的 Notion 資料庫範本：
[點擊此處查看 Notion 資料庫範本](https://www.notion.so/fae3816f7d3b83718e6101eb312f4c77?v=ec13816f7d3b835695168843e84fed36&source=copy_link)
*注意：複製後請記得在該資料庫的右上角「...」→「Connections」中加入您自己的 Integration 授權。*

### 頁面內容結構 (Page Content Structure)
* **Mention Date (@remind)**: 系統自動標註之提醒標籤。
* **我的見解 (My Insights)**: 供使用者填寫之批判性思考區域。
* **文章概要 (Executive Summary)**: 核心內容摘要。
* **特別單字 (Technical Vocabulary)**: 關鍵技術術語記錄。
* **原文複製 (Original Excerpts)**: 重要原文片段引用。

## 部署流程 (Implementation Guide)

1. **環境複製**
   使用 Git 指令克隆專案倉庫至本地或個人伺服器。

2. **Notion Integration 授權**
   於 Notion Developers 平台建立整合項目，並取得 Internal Integration Token。

3. **資料庫連線設定**
   於目標資料庫中啟用 Connections，並擷取 URL 中的 DATABASE_ID。

4. **GitHub Secrets 配置**
   於 Repository 設定中配置環境變數：`NOTION_TOKEN` 與 `DATABASE_ID`。

5. **CI/CD 自動化啟動**
   確認 `.github/workflows/daily-notion-sync.yml` 設定正確後，系統將依據 Cron 排程自動運作。

## 技術堆棧 (Technical Stack)

* **Java 17**: 核心邏輯開發，採用原生 java.net.http.HttpClient 確保輕量化。
* **GitHub Actions**: 負責系統自動化運維 (CI/CD) 與排程管理。
* **RESTful APIs**: 整合 arXiv, Hacker News, Wikipedia 與 Notion 接口。