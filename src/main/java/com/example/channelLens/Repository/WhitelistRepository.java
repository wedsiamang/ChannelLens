package com.example.channelLens.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.example.channelLens.Model.Whitelist;
import java.util.Optional;
import java.util.List;


@Repository
public interface WhitelistRepository extends JpaRepository<Whitelist, Long> {

    Optional<Whitelist> findBySystemName(String systemName); // findfind→find

    List<Whitelist> findByDepartment(String department);
}
