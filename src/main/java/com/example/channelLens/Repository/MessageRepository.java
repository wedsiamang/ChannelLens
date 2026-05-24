package com.example.channelLens.Repository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.example.channelLens.Model.Message;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    // tsで重複チェック用
    boolean existsByTs(String ts);

    @Query("SELECT m FROM Message m WHERE m.text LIKE %:keyword%")
List<Message> findByTextContaining(@Param("keyword") String keyword);
}
