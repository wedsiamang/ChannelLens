package com.example.channelLens.Repository;


import com.example.channelLens.Model.ChannelKpi;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelKpiRepository extends JpaRepository<ChannelKpi, Long> {
    // privateChannelIdでKPI設定を取得
    java.util.Optional<ChannelKpi> findByPrivateChannelId(String privateChannelId);
}