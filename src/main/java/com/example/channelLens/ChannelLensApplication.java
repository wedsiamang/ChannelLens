package com.example.channelLens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling  // ← 追加
@SpringBootApplication
public class ChannelLensApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChannelLensApplication.class, args);
	}

}
