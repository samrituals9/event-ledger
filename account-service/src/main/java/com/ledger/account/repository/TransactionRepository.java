package com.ledger.account.repository;

import com.ledger.account.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByEventId(String eventId);

    boolean existsByEventId(String eventId);

    List<Transaction> findByAccountIdOrderByEventTimestampAsc(String accountId);
}
