package com.ledgerx.wallet_service.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class WalletResponse {
    private UUID id;
    private String currency;
    private BigDecimal balance;
    private String status;
}