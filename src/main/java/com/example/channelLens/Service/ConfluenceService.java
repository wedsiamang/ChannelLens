package com.example.channelLens.Service;

import com.example.channelLens.Model.Whitelist;
import com.example.channelLens.Repository.WhitelistRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class ConfluenceService {

    private final WhitelistRepository whitelistRepository;
    private final RestTemplate restTemplate;

    @Value("${confluence.base-url}")
    private String baseUrl;

    @Value("${confluence.page-id}")
    private String pageId;

    @Value("${confluence.email}")
    private String email;

    @Value("${confluence.api-token}")
    private String apiToken;

    public ConfluenceService(WhitelistRepository whitelistRepository) {
        this.whitelistRepository = whitelistRepository;
        this.restTemplate = new RestTemplate();
    }

    // 起動時に自動でConfluenceから取得してH2に保存
    @PostConstruct
    public void syncWhitelistFromConfluence() {
        try {
            // Basic認証ヘッダー作成
            String credentials = email + ":" + apiToken;
            String encoded = Base64.getEncoder()
                .encodeToString(credentials.getBytes());

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Basic " + encoded);
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Confluence APIでページ本文取得
            String url = baseUrl
                + "/rest/api/content/" + pageId
                + "?expand=body.storage";

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate
                .exchange(url, HttpMethod.GET, entity, String.class);

            // JsoupでHTMLテーブルをパース
            String body = response.getBody();
            List<Whitelist> list = parseTable(body);

            // H2に全件保存（既存データは削除して入れ替え）
            whitelistRepository.deleteAll();
            whitelistRepository.saveAll(list);

            System.out.println("✅ Confluenceからホワイトリスト取得完了: "
                + list.size() + "件");

        } catch (Exception e) {
            System.err.println("❌ Confluence取得エラー: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<Whitelist> parseTable(String jsonBody) {
        List<Whitelist> result = new ArrayList<>();

        // JSONからbody.storage.valueを抜き出す（簡易パース）
        int start = jsonBody.indexOf("\"value\":\"") + 9;
        int end = jsonBody.indexOf("\",\"representation\"");
        if (start < 9 || end < 0) return result;

        String htmlEncoded = jsonBody.substring(start, end);
        // エスケープ文字を戻す
        String html = htmlEncoded
            .replace("\\n", "")
            .replace("\\\"", "\"")
            .replace("\\/", "/");

        // Jsoupでテーブルパース
        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("table tr");

        // 1行目はヘッダーなのでスキップ
        for (int i = 1; i < rows.size(); i++) {
            Elements cols = rows.get(i).select("td");
            if (cols.size() < 3) continue;

            Whitelist w = new Whitelist();
            w.setSystemName(cols.get(0).text().trim());
            w.setDepartment(cols.get(1).text().trim());
            w.setConditions(cols.get(2).text().trim());
            // 過去履歴は空でOK
            result.add(w);
        }
        return result;
    }
}