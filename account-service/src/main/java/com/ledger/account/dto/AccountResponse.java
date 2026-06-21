package com.ledger.account.dto;

import java.math.BigDecimal;
import java.util.List;

public class AccountResponse {

    private String accountId;
    private BigDecimal balance;
    private List<TransactionResponse> transactions;

    public AccountResponse(String accountId, BigDecimal balance, List<TransactionResponse> transactions) {
        this.accountId = accountId;
        this.balance = balance;
        this.transactions = transactions;
    }

    public String getAccountId() { return accountId; }
    public BigDecimal getBalance() { return balance; }
    public List<TransactionResponse> getTransactions() { return transactions; }
}
