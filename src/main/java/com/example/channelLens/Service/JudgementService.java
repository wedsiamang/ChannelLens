package com.example.channelLens.Service;

import com.example.channelLens.Model.Message;
import com.example.channelLens.Model.Whitelist;
import com.example.channelLens.Repository.MessageRepository;
import com.example.channelLens.Repository.WhitelistRepository;
import com.example.channelLens.Config.SlackConfig;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.chat.ChatPostMessageRequest;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class JudgementService {

    private final WhitelistRepository whitelistRepository;
    private final MessageRepository messageRepository;
    private final EmbeddingService embeddingService;
    private final SlackConfig slackConfig;

    @Value("${channel.private-channel-id}")
    private String privateChannelId;

    public JudgementService(
            WhitelistRepository whitelistRepository,
            MessageRepository messageRepository,
            EmbeddingService embeddingService,
            SlackConfig slackConfig) {
        this.whitelistRepository = whitelistRepository;
        this.messageRepository = messageRepository;
        this.embeddingService = embeddingService;
        this.slackConfig = slackConfig;
    }

    public void judge(String systemName, String department, String userId) {
        try {
            MethodsClient client = slackConfig.methodsClient();

            // ① ホワイトリスト完全一致検索
            Optional<Whitelist> match =
                whitelistRepository.findBySystemName(systemName);

            if (match.isPresent()) {
                // ✅ 一致 → 承認メッセージ
                Whitelist w = match.get();
                String message = String.format(
                    "✅ *ホワイトリスト一致*\n" +
                    "申請者: <@%s>\n" +
                    "システム名: %s\n" +
                    "所属: %s\n" +
                    "条件: %s",
                    userId,           // applicant → userId に修正
                    w.getSystemName(),
                    w.getDepartment(),
                    w.getConditions()  // Whitelistのフィールド名に合わせる
                );
                postMessage(client, message);

            } else {
                // ❌ 未登録 → MessageHandlerと同じロジックで類似5件
                float[] queryVector =
                    embeddingService.getEmbedding(systemName);

                List<Message> similar = messageRepository.findAll()
                    .stream()
                    .filter(m -> m.getEmbedding() != null)
                    .map(m -> Map.entry(m,
                        embeddingService.cosineSimilarity(
                            queryVector,
                            parseEmbedding(m.getEmbedding()))))
                    .sorted(Map.Entry
                        .<Message, Double>comparingByValue().reversed())
                    .filter(e -> e.getValue() > 0.7)
                    .limit(5)
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toList());

                StringBuilder sb = new StringBuilder();
                sb.append(String.format(
                    "⚠️ *ホワイトリスト未登録*\n" +
                    "申請者: <@%s>\n" +
                    "所属: %s\n" +
                    "システム名: %s\n\n" +
                    "📎 類似過去案件:\n",
                    userId, department, systemName
                ));

                if (similar.isEmpty()) {
                    sb.append("（類似案件なし）");
                } else {
                    for (Message m : similar) {
                        var pRes = client.chatGetPermalink(r -> r
                            .channel(m.getChannelId())
                            .messageTs(m.getTs()));
                        sb.append(pRes.getPermalink()).append("\n");
                    }
                }
                postMessage(client, sb.toString());
            }

        } catch (Exception e) {
            System.err.println("❌ JudgementService エラー: "
                + e.getMessage());
            e.printStackTrace();
        }
    }

    private void postMessage(MethodsClient client, String text)
            throws Exception {
        client.chatPostMessage(ChatPostMessageRequest.builder()
            .channel(privateChannelId)
            .text(text)
            .blocks(List.of(
                SectionBlock.builder()
                    .text(MarkdownTextObject.builder()
                        .text(text).build())
                    .build()
            ))
            .build());
    }

    // MessageHandlerと同じ変換ロジック
    private float[] parseEmbedding(String embeddingStr) {
        String[] parts = embeddingStr
            .replace("[", "").replace("]", "").split(", ");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i]);
        }
        return result;
    }
}