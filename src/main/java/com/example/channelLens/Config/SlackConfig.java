package com.example.channelLens.Config;

import com.slack.api.bolt.App;
import com.slack.api.bolt.AppConfig;
import com.slack.api.methods.MethodsClient;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SlackConfig {

    @Value("${slack.bot-token}")
    private String botToken;
    public String getBotToken(){return botToken;}

    @Value("${slack.app-token}")
    private String appToken;

    @Value("${channel.private-channel-id}")
    private String privateChannelId;

    public String getPrivateChannelId(){return privateChannelId;}

    @Value("${channel.public-channel-id}")
    private String publicChannelId;

    public String getPublicChannelId(){return publicChannelId;}

    @Bean
    public AppConfig appConfig() {
        return AppConfig.builder()
                .singleTeamBotToken(botToken)
                .build();
    }

   @Bean
   public MethodsClient methodsClient() {
    return com.slack.api.Slack.getInstance().methods(botToken);
}

    @Bean
    public App app(AppConfig appConfig) {
        return new App(appConfig);
    }

    public String getAppToken() { return appToken; }
}