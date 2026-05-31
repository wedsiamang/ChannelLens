package com.example.channelLens.Model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import lombok.Data;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;


@Entity
@Data
@Table
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String channelId;
    private String messageTs;
    private String permalink;
    private LocalDateTime  postedAt;
    private LocalDateTime  startedAt;
    private LocalDateTime  resolvedAt;
    private String status;
    private String botMessageTs;
    private String privateChannelId;

}


