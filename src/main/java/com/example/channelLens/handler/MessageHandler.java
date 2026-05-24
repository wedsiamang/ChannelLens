package com.example.channelLens.handler;

import com.example.channelLens.Config.SlackConfig;
import com.example.channelLens.Model.Message;
import com.slack.api.bolt.App;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.MethodsClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import com.example.channelLens.Repository.MessageRepository;
import com.example.channelLens.Service.EmbeddingService;

import java.util.List;
import java.util.Map;


@Component
public class MessageHandler implements CommandLineRunner {

    private final App app;
    private final SlackConfig slackConfig;
    private final MessageRepository messageRepository;
    private final EmbeddingService embeddingService;

    @Value("${channel.private-channel-id}")
        private String privateChannelId;

    @Value("${channel.public-channel-id}")
    private String publicChannelId;

    public MessageHandler(App app, SlackConfig slackConfig,MessageRepository messageRepository,EmbeddingService embeddingService) {
        this.app = app;
        this.slackConfig = slackConfig;
        this.messageRepository = messageRepository;
        this.embeddingService = embeddingService;
    }

    @Override
    public void run(String... args) throws Exception {

        app.event(com.slack.api.model.event.MessageEvent.class, (payload, ctx) -> {
              if (payload.getEvent().getBotId() != null) return ctx.ack();
    if (payload.getEvent().getSubtype() != null) return ctx.ack();
            
            
            String text = payload.getEvent().getText()
                    .replaceAll("<@[A-Z0-9]+>", "").trim();
            handle(text, payload.getEvent().getChannel(), payload.getEvent().getTs(), ctx.client());
            return ctx.ack();
        });

        new SocketModeApp(slackConfig.getAppToken(), app).start();
    }
    // handle()をMessageHandler内に追加
private void handle(String text, String channel, String ts, MethodsClient client) {

    try {
        // 1. 新規投稿のpermalinkを取得して内部チャンネルに転送
        var permalinkRes = client.chatGetPermalink(r -> r
            .channel(channel).messageTs(ts));
        String permalink = permalinkRes.getPermalink();

        // 2. キーワード検索
   //     List<com.example.channelLens.Model.Message> results 
       //     = messageRepository.findByTextContaining(text);

// 1. 投稿テキストをEmbedding化
float[] queryVector = embeddingService.getEmbedding(text);

// 2. H2の全メッセージを取得
List<Message> allMessages = messageRepository.findAll();

// 3. コサイン類似度でソート
// 変更
List<Message> results = allMessages.stream()
    .filter(m -> m.getEmbedding() != null && !m.getTs().equals(ts))
    .map(m -> Map.entry(m, embeddingService.cosineSimilarity(
        queryVector, parseEmbedding(m.getEmbedding()))))
    .sorted(Map.Entry.<Message, Double>comparingByValue().reversed())
    .limit(5)
    .filter(entry -> entry.getValue() > 0.7)
    .map(Map.Entry::getKey)
    .collect(java.util.stream.Collectors.toList());


        // 3. 内部チャンネルに投稿
        StringBuilder sb = new StringBuilder();
        sb.append(permalink).append("\n【類似過去事例】\n");
        if (results.isEmpty()) {
            sb.append("なし") ;
        } else {
           for (var m : results) {
    var pRes = client.chatGetPermalink(r -> r
        .channel(m.getChannelId())
        .messageTs(m.getTs())
    );
    sb.append(pRes.getPermalink()).append("\n");
}
        }
        client.chatPostMessage(r -> r
            .channel(privateChannelId).text(sb.toString()));

    } catch (Exception e) {
        e.printStackTrace();
    }
}
// Embedding文字列→float[]に変換
private float[] parseEmbedding(String embeddingStr) {
    String[] parts = embeddingStr.replace("[","").replace("]","").split(", ");
    float[] result = new float[parts.length];
    for(int i = 0; i < parts.length; i++) {
        result[i] = Float.parseFloat(parts[i]);
    }
    return result;
}

}
