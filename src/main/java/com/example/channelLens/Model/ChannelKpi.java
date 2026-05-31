package com.example.channelLens.Model;


import jakarta.persistence.Id;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Data;
import jakarta.persistence.Table;

@Entity
@Data
@Table
public class ChannelKpi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String channelId;      // どのチャンネルのKPIか
    private String channelName;    // 表示名
    private Long kpiMinutes;
    private String privateChannelId;      
                                   
}
