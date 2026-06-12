package com.example.channelLens.handler;

import com.example.channelLens.Config.SlackConfig;
import com.example.channelLens.Model.Message;
import com.slack.api.bolt.App;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.MethodsClient;
import com.example.channelLens.Service.JudgementService;
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
import com.slack.api.bolt.context.builtin.ActionContext;
import com.slack.api.bolt.context.builtin.GlobalShortcutContext;
import com.slack.api.bolt.request.builtin.BlockActionRequest;
import com.slack.api.bolt.request.builtin.GlobalShortcutRequest;
// Block Kit - ブロック本体
import com.slack.api.model.block.Blocks;
// Block Kit - 要素（ボタンなど）
import com.slack.api.model.block.element.ButtonElement;
// Block Kit - テキスト
import com.slack.api.model.block.composition.PlainTextObject;
// モーダル
import com.slack.api.model.view.Views;


@Component
public class MessageHandler implements CommandLineRunner {

    private final JudgementService judgementService;
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
        messageRepository,EmbeddingService embeddingService,TicketRepository ticketRepository,
    JudgementService judgementService) {
        this.app = app;
        this.slackConfig = slackConfig;
        this.messageRepository = messageRepository;
        this.embeddingService = embeddingService;
        this.ticketRepository = ticketRepository;
        this.judgementService = judgementService;
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

// MessageHandler の run() メソッド内、既存コードの後に追加

// ───────────────────────────────────────
// ① ワークフロー起動ボタン（クロス申請）
// ───────────────────────────────────────
// ① cross_start
app.blockAction("cross_start", (BlockActionRequest req, ActionContext ctx) -> {
    ctx.client().viewsOpen(r -> r
        .triggerId(req.getPayload().getTriggerId())
        .view(Views.view(v -> v
            .type("modal")
            .callbackId("cross_select")
            .title(Views.viewTitle(t ->
                t.type("plain_text").text("クロス申請")))
            .blocks(List.of(
                Blocks.section(s -> s
                    .text(MarkdownTextObject.builder()
                        .text("申請内容を選択してください。").build())
                ),
                Blocks.actions(a -> a
                    .blockId("select_block")
                    .elements(List.of(
                        ButtonElement.builder()
                            .actionId("cross_apply")
                            .text(PlainTextObject.builder()
                                .text("📋 申請").build())
                            .style("primary")
                            .build(),
                        ButtonElement.builder()
                            .actionId("cross_other")
                            .text(PlainTextObject.builder()
                                .text("💬 その他の質問").build())
                            .build()
                    ))
                )
            ))
        ))
    );
    return ctx.ack();
});

// ② cross_apply
app.blockAction("cross_apply", (BlockActionRequest req, ActionContext ctx) -> {
    ctx.client().viewsUpdate(r -> r
        .viewId(req.getPayload().getView().getId())
        .view(Views.view(v -> v
            .type("modal")
            .callbackId("cross_form")
            .title(Views.viewTitle(t ->
                t.type("plain_text").text("システム申請")))
            .submit(Views.viewSubmit(s ->
                s.type("plain_text").text("送信")))
            .close(Views.viewClose(c ->
                c.type("plain_text").text("キャンセル")))
            .blocks(List.of(
                Blocks.input(i -> i
                    .blockId("system_block")
                    .label(PlainTextObject.builder()
                        .text("申請するシステム名").build())
                    .element(com.slack.api.model.block.element
                        .PlainTextInputElement.builder()
                        .actionId("system_name_input")
                        .placeholder(PlainTextObject.builder()
                            .text("例: Slack, Figma").build())
                        .build())
                ),
                Blocks.input(i -> i
                    .blockId("dept_block")
                    .label(PlainTextObject.builder()
                        .text("所属部署").build())
                    .element(com.slack.api.model.block.element
                        .PlainTextInputElement.builder()
                        .actionId("dept_input")
                        .placeholder(PlainTextObject.builder()
                            .text("例: 開発部、営業部").build())
                        .build())
                )
            ))
        ))
    );
    return ctx.ack();
});

// ③ cross_other
app.blockAction("cross_other", (BlockActionRequest req, ActionContext ctx) -> {
    ctx.client().viewsUpdate(r -> r
        .viewId(req.getPayload().getView().getId())
        .view(Views.view(v -> v
            .type("modal")
            .callbackId("cross_other_form")
            .title(Views.viewTitle(t ->
                t.type("plain_text").text("その他の質問")))
            .submit(Views.viewSubmit(s ->
                s.type("plain_text").text("送信")))
            .close(Views.viewClose(c ->
                c.type("plain_text").text("キャンセル")))
            .blocks(List.of(
                Blocks.input(i -> i
                    .blockId("question_block")
                    .label(PlainTextObject.builder()
                        .text("質問内容").build())
                    .element(com.slack.api.model.block.element
                        .PlainTextInputElement.builder()
                        .actionId("question_input")
                        .multiline(true)
                        .placeholder(PlainTextObject.builder()
                            .text("質問内容を入力してください。").build())
                        .build())
                )
            ))
        ))
    );
    return ctx.ack();
});

// その他フォーム送信
app.viewSubmission("cross_other_form", (req, ctx) -> {
    String userId = req.getPayload().getUser().getId();
    String question = req.getPayload().getView()
        .getState().getValues()
        .get("question_block")
        .get("question_input")
        .getValue();

    // privateチャンネルに投稿
    ctx.client().chatPostMessage(r -> r
        .channel(privateChannelId)
        .text(String.format(
            "💬 <@%s> さんからの質問:\n%s\n\n担当者が確認しています。",
            userId, question))
    );
    return ctx.ack();
});

// ───────────────────────────────────────
// ④ フォーム送信 → ホワイトリスト判定
// ───────────────────────────────────────
app.viewSubmission("cross_form", (req, ctx) -> {
    String userId = req.getPayload().getUser().getId();

    // フォームから値を取得
    String systemName = req.getPayload().getView()
        .getState().getValues()
        .get("system_block")
        .get("system_name_input")
        .getValue();

    String department = req.getPayload().getView()
        .getState().getValues()
        .get("dept_block")
        .get("dept_input")
        .getValue();

    // JudgementServiceで判定・投稿
    judgementService.judge(systemName, department, userId);

    return ctx.ack();
});
// ───────────────────────────────────────
// ⑤ グローバルショートカット → 最初のモーダル表示
// ───────────────────────────────────────
app.globalShortcut("cross_request", (GlobalShortcutRequest req, GlobalShortcutContext ctx) -> {
    ctx.client().viewsOpen(r -> r
        .triggerId(req.getPayload().getTriggerId())
        .view(Views.view(v -> v
            .type("modal")
            .callbackId("cross_select")
            .title(Views.viewTitle(t ->
                t.type("plain_text").text("クロス申請")))
            .blocks(List.of(
                Blocks.section(s -> s
                    .text(MarkdownTextObject.builder()
                        .text("申請内容を選択してください。").build())
                ),
                Blocks.actions(a -> a
                    .blockId("select_block")
                    .elements(List.of(
                        ButtonElement.builder()
                            .actionId("cross_apply")
                            .text(PlainTextObject.builder()
                                .text("📋 申請").build())
                            .style("primary")
                            .build(),
                        ButtonElement.builder()
                            .actionId("cross_other")
                            .text(PlainTextObject.builder()
                                .text("💬 その他の質問").build())
                            .build()
                    ))
                )
            ))
        ))
    );
    return ctx.ack();
});

new SocketModeApp(slackConfig.getAppToken(), app).start();

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
