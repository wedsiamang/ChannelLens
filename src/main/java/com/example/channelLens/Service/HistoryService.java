package com.example.channelLens.Service;


import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;

import com.example.channelLens.Model.Message;


import org.springframework.stereotype.Service;

import com.slack.api.methods.MethodsClient;

import com.slack.api.methods.response.conversations.ConversationsHistoryResponse;

import com.example.channelLens.Repository.MessageRepository;
import com.slack.api.Slack;

import com.example.channelLens.Config.SlackConfig;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;


@Service
public class HistoryService  {

    private final EmbeddingService embeddingService;
    private final MessageRepository messageRepository;
    private final SlackConfig slackConfig;

    @Value("${channel.private-channel-id}")
        private String privateChannelId;

    @Value("${channel.public-channel-id}")
    private String publicChannelId;

public HistoryService(MessageRepository messageRepository, SlackConfig slackConfig,EmbeddingService embeddingService) {
    this.messageRepository = messageRepository;
    this.slackConfig = slackConfig;
    this.embeddingService = embeddingService;
}

    @PostConstruct
    public void fetchHistory(){
        try{
            MethodsClient client = Slack.getInstance().methods(slackConfig.getBotToken());

            String oldist = String.valueOf(LocalDateTime.now().minusYears(2).toEpochSecond(ZoneOffset.of("+09:00")));
        String cursor = null;
        ConversationsHistoryResponse response = null;
        do{
            final String currentCursor = cursor;
            //conversations.history を呼ぶ
             response = client.conversationsHistory(r -> r
                .channel(publicChannelId)
                .oldest(oldist)
                .cursor(currentCursor)
                .limit(200)
            );
            

            //メッセージをH2に保存
            for(var msg : response.getMessages()){
                // botの投稿を除外する
                if (msg.getSubtype() != null || msg.getUser() == null) {
                continue;
                }
                if(!messageRepository.existsByTs(msg.getTs())){
                Message ms = new Message();
            ms.setChannelId(publicChannelId);
            ms.setPostedAt(LocalDateTime.now());
            ms.setText(msg.getText());
            ms.setTs(msg.getTs());
            System.out.println("user: " + msg.getUser() + " text: " + msg.getText());
            ms.setUserId(msg.getUser());
            float[] vector = embeddingService.getEmbedding(msg.getText());
            ms.setEmbedding(Arrays.toString(vector));
            messageRepository.save(ms);
            }
        }
        //page数が少ない場合ページネーション不要。nullチェック
            String nextCursor = null;
            if(response.getResponseMetadata() != null){
                nextCursor = response.getResponseMetadata().getNextCursor();
            }
        cursor = nextCursor;
        //ページ数が多い場合こっち。cursor = response.getResponseMetadata().getNextCursor();
        }while(response.isHasMore()
        && response.getResponseMetadata() !=null
        && response.getResponseMetadata().getNextCursor() !=null);
        //has_more で置き換え

        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
