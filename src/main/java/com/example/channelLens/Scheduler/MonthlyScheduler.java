package com.example.channelLens.Scheduler;

import com.example.channelLens.Model.Ticket;
import com.example.channelLens.Model.ChannelKpi;
import com.example.channelLens.Repository.TicketRepository;
import com.example.channelLens.Repository.ChannelKpiRepository;
import com.slack.api.methods.MethodsClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;


@Component
public class MonthlyScheduler {

    private final TicketRepository ticketRepository;
    private final ChannelKpiRepository channelKpiRepository;
    private final MethodsClient client;

    public MonthlyScheduler(TicketRepository ticketRepository,
                            ChannelKpiRepository channelKpiRepository,
                            MethodsClient client) {
        this.ticketRepository = ticketRepository;
        this.channelKpiRepository = channelKpiRepository;
        this.client = client;
    }

    // 翌月1日 午前9時に実行
    @Scheduled(cron = "0 41 15 31 * *")
    public void sendMonthlyReport() throws Exception {

        // 先月の範囲を算出
        LocalDateTime now = LocalDateTime.now();
        // LocalDateTime start = now.minusMonths(1)
        //     .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        // LocalDateTime end = now.withDayOfMonth(1)
        //     .withHour(0).withMinute(0).withSecond(0);
// 変更後（今月：テスト用）
LocalDateTime start = now
    .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
LocalDateTime end = now;


        // privateチャンネルごとに集計
        List<ChannelKpi> kpiList = channelKpiRepository.findAll();

        for (ChannelKpi kpi : kpiList) {
            String privateChannelId = kpi.getPrivateChannelId();

            // 先月のチケットを取得
            List<Ticket> tickets = ticketRepository
                .findByPrivateChannelIdAndPostedAtBetween(
                    privateChannelId, start, end);

            if (tickets.isEmpty()) continue;

            // 集計
            long total = tickets.size();

            long resolved = tickets.stream()
                .filter(t -> "解決済み".equals(t.getStatus())
                          || "自己解決".equals(t.getStatus()))
                .count();

            long escalated = tickets.stream()
                .filter(t -> "エスカレ".equals(t.getStatus()))
                .count();

            long selfResolved = tickets.stream()
                .filter(t -> "自己解決".equals(t.getStatus()))
                .count();

            double selfResolveRate = (double) selfResolved / total * 100;

            // KPI達成率（startedAt→resolvedAt が閾値以内）
            long kpiAchieved = tickets.stream()
                .filter(t -> t.getStartedAt() != null && t.getResolvedAt() != null)
                .filter(t -> ChronoUnit.MINUTES.between(
                    t.getStartedAt(), t.getResolvedAt()) <= kpi.getKpiMinutes())
                .count();

            long kpiTarget = tickets.stream()
                .filter(t -> t.getStartedAt() != null && t.getResolvedAt() != null)
                .count();

            double kpiRate = kpiTarget > 0
                ? (double) kpiAchieved / kpiTarget * 100 : 0;

            // メッセージ組み立て
            String month = start.getMonth().getValue() + "月";
            String report = String.format("""
                📊 *%s 月次レポート*
                　総件数：%d件
                　解決済み合計：%d件
                　エスカレ件数：%d件
                　自己解決割合：%.1f%%
                　KPI達成率（%d分以内）：%.1f%%
                """, month, total, resolved, escalated,
                    selfResolveRate, kpi.getKpiMinutes(), kpiRate);

            // privateチャンネルに通知
            client.chatPostMessage(r -> r
                .channel(privateChannelId)
                .text(report)
            );
        }
    }
}