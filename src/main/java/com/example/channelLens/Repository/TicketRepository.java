package com.example.channelLens.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.channelLens.Model.Ticket;

import java.time.LocalDateTime;
import java.util.List;


@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByMessageTs(String messageTs);

List<Ticket> findByBotMessageTs(String botMessageTs);

List<Ticket> findByPrivateChannelIdAndPostedAtBetween(
    String privateChannelId, LocalDateTime start, LocalDateTime end);
}