package com.example.channelLens.handler;

import com.example.channelLens.Config.SlackConfig;
import com.example.channelLens.Model.Message;
import com.slack.api.bolt.App;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.MethodsClient;

import org.checkerframework.checker.units.qual.t;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import com.example.channelLens.Repository.MessageRepository;
import com.example.channelLens.Service.EmbeddingService;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.example.channelLens.Model.Ticket;
import com.example.channelLens.Repository.TicketRepository;
import com.slack.api.model.event.MessageChangedEvent;
import com.slack.api.model.event.MessageEvent;
import com.slack.api.model.event.ReactionAddedEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;




@Component
public class MessageHandler implements CommandLineRunner {

    private final App app;
    private final SlackConfig slackConfig;
    private final MessageRepository messageRepository;
    private final EmbeddingService embeddingService;
    private final TicketRepository ticketRepository;

    @Value("${channel.private-channel-id}")
        private String privateChannelId;

    @Value("${channel.public-channel-id}")
    private String publicChannelId;

    public MessageHandler(App app, SlackConfig slackConfig,MessageRepository 
        messageRepository,EmbeddingService embeddingService,TicketRepository ticketRepository) {
        this.app = app;
        this.slackConfig = slackConfig;
        this.messageRepository = messageRepository;
        this.embeddingService = embeddingService;
        this.ticketRepository = ticketRepository;
    }

@Override
public void run(String... args) throws Exception {

    app.event(MessageChangedEvent.class, (payload, ctx) -> {
    return ctx.ack();
});

    // メッセージイベント
    app.event(MessageEvent.class, (payload, ctx) -> {
        if (payload.getEvent().getBotId() != null) return ctx.ack();
        if (payload.getEvent().getSubtype() != null) return ctx.ack();
        String text = payload.getEvent().getText()
            .replaceAll("<@[A-Z0-9]+>", "").trim();
        handle(text, payload.getEvent().getChannel(), 
            payload.getEvent().getTs(), ctx.client());
        return ctx.ack();
    });
    // リアクションイベント
app.event(ReactionAddedEvent.class, (payload, ctx) -> {
    String reaction    = payload.getEvent().getReaction();
    String reactedTs   = payload.getEvent().getItem().getTs();
    String reactedChannel = payload.getEvent().getItem().getChannel();

    // ① privateチャンネル以外のスタンプは無視
    if (!privateChannelId.equals(reactedChannel)) return ctx.ack();

    // ② BotMessageTSでTicket取得
    List<Ticket> tickets = ticketRepository.findByBotMessageTs(reactedTs);
    if (tickets.isEmpty()) return ctx.ack();
    Ticket ticket = tickets.get(0);

    // ③ スタンプ種別でDB更新
    switch (reaction) {
        case "eyes" -> {
            ticket.setStartedAt(LocalDateTime.now());
            ticket.setStatus("対応中");
        }
        case "white_check_mark" -> {
            ticket.setResolvedAt(LocalDateTime.now());
            ticket.setStatus("解決済み");
        }
        case "sos" -> {
            ticket.setStatus("エスカレ");
        }
        case "+1" -> {
            ticket.setResolvedAt(LocalDateTime.now());
            ticket.setStatus("自己解決");
        }
        default -> { return ctx.ack(); } // 対象外スタンプは無視
    }
    ticketRepository.save(ticket);

    // ④ privateチャンネルに通知
    String notifyText = switch (reaction) {
        case "eyes"              -> "👀 対応開始されました：" + ticket.getPermalink();
        case "white_check_mark"  -> "✅ 解決済みになりました：" + ticket.getPermalink();
        case "sos"               -> "🆘 エスカレされました：" + ticket.getPermalink();
        case "+1"          -> "👍 自己解決されました：" + ticket.getPermalink();
        default                  -> "";
    };

    String finalNotifyText = notifyText;
    // ctx.client().chatPostMessage(r -> r
    //     .channel(privateChannelId)
    //     .text(finalNotifyText)
    // );
    String botTs = ticket.getBotMessageTs();
ctx.client().chatPostMessage(r -> r
    .channel(privateChannelId)
    .text(finalNotifyText)
    .threadTs(botTs)  // ← 親メッセージのtsを指定
);

    return ctx.ack();
});

    // startボタン
//     app.blockAction(Pattern.compile("start_.*"), (payload, ctx) -> {
//         String messageTs = payload.getPayload().getActions().get(0)
//             .getActionId().replace("start_", "");
//         // ticketRepository.findByMessageTs(messageTs).ifPresentOrElse(
//         //     t -> { /* 既存あり → 何もしない */ },
//         //     () -> {
//         List<Ticket> existing = ticketRepository.findByMessageTs(messageTs);
// if (!existing.isEmpty()) {
//     Ticket t = existing.get(0);
//     t.setStartedAt(LocalDateTime.now());
//     t.setStatus("対応中");
//     ticketRepository.save(t);
// }
//   //      );
//         return ctx.ack();
//     });

    // resolveボタン
//     app.blockAction(Pattern.compile("resolve_.*"), (payload, ctx) -> {
//         String messageTs = payload.getPayload().getActions().get(0)
//             .getActionId().replace("resolve_", "");
//         double tsDouble = Double.parseDouble(messageTs);
//         LocalDateTime postedAt = LocalDateTime.ofEpochSecond(
//             (long) tsDouble, 0, java.time.ZoneOffset.of("+09:00"));
//         // ticketRepository.findByMessageTs(messageTs).ifPresentOrElse(
//         //     t -> {
//         //         t.setStatus("解決済み");
//         //         t.setResolvedAt(LocalDateTime.now());
//         //         ticketRepository.save(t);
//         //     },
//         //     () -> {

// List<Ticket> existing = ticketRepository.findByMessageTs(messageTs);
// if (!existing.isEmpty()) {
//     Ticket t = existing.get(0);
//                 t.setStatus("解決済み");
//                 t.setResolvedAt(LocalDateTime.now());
//                 ticketRepository.save(t);
//             }
//       //  );
//         return ctx.ack();
//     });

    new SocketModeApp(slackConfig.getAppToken(), app).start();
}
private void handle(String text, String channel, String ts, MethodsClient client) {

    try {
        if (!ticketRepository.findByMessageTs(ts).isEmpty()) {
    return;
}
        // 1. 新規投稿のpermalinkを取得して内部チャンネルに転送
        var permalinkRes = client.chatGetPermalink(r -> r
            .channel(channel).messageTs(ts));
        String permalink = permalinkRes.getPermalink();
        // chatPostMessage の結果からtsを取得


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
    .filter(entry -> entry.getValue() > 0.7)
    .limit(5)
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
        // client.chatPostMessage(r -> r
        //     .channel(privateChannelId).text(sb.toString()));
       var postRes = client.chatPostMessage(r -> r
    .channel(privateChannelId)
    .text(sb.toString())
    .blocks(List.of(
        SectionBlock.builder()
            .text(MarkdownTextObject.builder().text(sb.toString()).build())
            .build()
    ))
);
// Ticket作成
Ticket ticket = new Ticket();
ticket.setChannelId(channel);
ticket.setMessageTs(ts);
ticket.setPermalink(permalink);
double tsDouble = Double.parseDouble(ts);
ticket.setPostedAt(LocalDateTime.ofEpochSecond(
    (long) tsDouble, 0, java.time.ZoneOffset.of("+09:00")));
ticket.setStatus("未対応");
ticket.setPrivateChannelId(privateChannelId); 
ticket.setBotMessageTs(postRes.getTs());
ticketRepository.save(ticket);


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
