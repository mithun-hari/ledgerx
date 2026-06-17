package com.ledgerx.wallet_service.service;

import com.ledgerx.wallet_service.config.FxServiceClient;
import com.ledgerx.wallet_service.dto.*;
import com.ledgerx.wallet_service.entity.*;
import com.ledgerx.wallet_service.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final FxServiceClient fxServiceClient;

    // ── Create a new wallet ───────────────────────────────────────────────────
    public WalletResponse createWallet(UUID userId, CreateWalletRequest request) {

        // 1. Reject duplicate currency wallet
        if (walletRepository.existsByUserIdAndCurrency(userId, request.getCurrency())) {
            throw new RuntimeException(
                    "You already have a " + request.getCurrency() + " wallet"
            );
        }

        // 2. Build and save — balance always starts at zero
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .currency(request.getCurrency())
                .build();

        Wallet saved = walletRepository.save(wallet);
        return toResponse(saved);
    }

    // ── Get all wallets for a user ────────────────────────────────────────────
    public List<WalletResponse> getWallets(UUID userId) {
        return walletRepository.findByUserId(userId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ── Transfer between two wallets ──────────────────────────────────────────
    @Transactional  // entire method is one atomic DB transaction
    public TransferResponse transfer(UUID initiatorId, TransferRequest request) {

        // 1. Idempotency — if key seen before, return existing result
        var existing = transactionRepository
                .findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            Transaction tx = existing.get();
            return TransferResponse.builder()
                    .transactionId(tx.getId())
                    .status(tx.getStatus())
                    .amount(tx.getAmount())
                    .currency(tx.getCurrency())
                    .senderWalletId(request.getSenderWalletId())
                    .receiverWalletId(request.getReceiverWalletId())
                    .build();
        }

        // Prevent self-transfer
        if (request.getSenderWalletId().equals(request.getReceiverWalletId())) {
            throw new RuntimeException("Cannot transfer to the same wallet");
        }
        // 2. Load both wallets
        Wallet sender = walletRepository.findById(request.getSenderWalletId())
                .orElseThrow(() -> new RuntimeException("Sender wallet not found"));

        Wallet receiver = walletRepository.findById(request.getReceiverWalletId())
                .orElseThrow(() -> new RuntimeException("Receiver wallet not found"));

        // 3. Check sender has enough balance
        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient balance");
        }

        // 4. Check both wallets are active
        if (!sender.getStatus().equals("ACTIVE") ||
                !receiver.getStatus().equals("ACTIVE")) {
            throw new RuntimeException("One or both wallets are not active");
        }

        // 5. Create transaction record — starts as PENDING
        Transaction transaction = Transaction.builder()
                .initiatorId(initiatorId)
                .type("TRANSFER")
                .status("PENDING")
                .amount(request.getAmount())
                .currency(sender.getCurrency())
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        Transaction savedTx = transactionRepository.save(transaction);

        // 6. Fetch FX rate if currencies differ
        BigDecimal fxRate = fxServiceClient.getExchangeRate(
                sender.getCurrency(),
                receiver.getCurrency()
        );

        // 7. Calculate converted amount for receiver
        BigDecimal convertedAmount = request.getAmount()
                .multiply(fxRate)
                .setScale(8, java.math.RoundingMode.HALF_UP);

        // 8. Calculate new balances
        var newSenderBalance   = sender.getBalance().subtract(request.getAmount());
        var newReceiverBalance = receiver.getBalance().add(convertedAmount);

        // 7. Write DEBIT ledger entry for sender
        ledgerEntryRepository.save(LedgerEntry.builder()
                .walletId(sender.getId())
                .transactionId(savedTx.getId())
                .entryType("DEBIT")
                .amount(request.getAmount())
                .currency(sender.getCurrency())
                .runningBalance(newSenderBalance)
                .build());

        // 8. Write CREDIT ledger entry for receiver
        ledgerEntryRepository.save(LedgerEntry.builder()
                .walletId(receiver.getId())
                .transactionId(savedTx.getId())
                .entryType("CREDIT")
                .amount(convertedAmount)
                .currency(receiver.getCurrency())
                .runningBalance(newReceiverBalance)
                .build());

        // 9. Update both wallet balances
        sender.setBalance(newSenderBalance);
        receiver.setBalance(newReceiverBalance);
        walletRepository.save(sender);
        walletRepository.save(receiver);

        // 10. Mark transaction as COMPLETED
        savedTx.setStatus("COMPLETED");
        transactionRepository.save(savedTx);

        return TransferResponse.builder()
                .transactionId(savedTx.getId())
                .status("COMPLETED")
                .amount(request.getAmount())
                .currency(sender.getCurrency())
                .convertedAmount(convertedAmount)
                .receiverCurrency(receiver.getCurrency())
                .fxRate(fxRate)
                .senderWalletId(sender.getId())
                .receiverWalletId(receiver.getId())
                .build();
    }

    // ── Helper: entity → response ─────────────────────────────────────────────
    private WalletResponse toResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .currency(wallet.getCurrency())
                .balance(wallet.getBalance())
                .status(wallet.getStatus())
                .build();
    }
}