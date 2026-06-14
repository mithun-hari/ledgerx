package com.ledgerx.wallet_service.repository;

import com.ledgerx.wallet_service.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findByInitiatorIdOrderByCreatedAtDesc(UUID initiatorId);
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
}