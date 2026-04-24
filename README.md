# Daily-Notion-English-Booster

這是一個為了「養成技術閱讀習慣」而誕生的自動化系統。每天早上，它會從 Medium 自動挑選一則技術文章，並整齊地推送到妳的 Notion 儀表板中，讓學習變更有儀式感。

#核心特色
自動化推送：利用 GitHub Actions，每天準時在雲端執行，電腦關機也能動。

Notion 整合：直接進入妳的精美圖庫（Gallery），方便後續筆記與評論。

隨機學習：從演算法、AI 到軟體工程，每天都有不同的技術驚喜。

#如何「載來用」？ 
1. 複製 Notion 範本
先將我好做好的「一頁式學習儀表板」複製到妳自己的 Notion 中：
https://invited-bath-83e.notion.site/fae3816f7d3b83718e6101eb312f4c77?v=ec13816f7d3b835695168843e84fed36&source=copy_link
(進入連結後，點擊右上角的 Duplicate 即可)

2. 叉取 (Fork) 此倉庫
點擊 GitHub 頁面右上角的 Fork，將這套 Java 程式碼帶回妳的帳號下。

3. 設定妳的「密鑰 (Secrets)」
為了安全，我們必須把鑰匙藏起來。請至 GitHub 倉庫設定：
Settings -> Secrets and variables -> Actions -> New repository secret

請新增以下兩把鑰匙：

NOTION_TOKEN：您的 Notion Integration Token。

DATABASE_ID：您剛剛複製的 Notion 資料庫 ID。

#技術細節
語言：Java 17+

API：Notion API v1

自動化：GitHub Actions (Cron Job)

UI 預覽：Notion Gallery View

#使用說明
手動記錄：機器人會幫妳填好日期跟標籤，但最珍貴的心得，還是要靠妳親手打上去。
