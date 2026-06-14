package com.ledgerx.wallet_service.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class TransferResponse {
    private UUID transactionId;
    private String status;
    private BigDecimal amount;
    private String currency;
    private UUID senderWalletId;
    private UUID receiverWalletId;
}