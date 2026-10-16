package com.archimstechcorner.marketplace.features.finance.application;

import com.archimstechcorner.marketplace.features.finance.domain.model.SellerLedgerEntry;
import com.archimstechcorner.marketplace.features.finance.domain.repository.SellerLedgerEntryRepository;
import com.archimstechcorner.marketplace.features.sellers.domain.model.Seller;
import com.archimstechcorner.marketplace.features.sellers.domain.repository.SellerRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class SellerBalanceService {

    private final SellerRepository sellerRepository;
    private final SellerLedgerEntryRepository ledgerEntryRepository;

    public SellerBalanceService(SellerRepository sellerRepository,
                                SellerLedgerEntryRepository ledgerEntryRepository) {
        this.sellerRepository = sellerRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    public record SellerBalanceSummary(
            String sellerId,
            String sellerName,
            BigDecimal currentBalance,
            BigDecimal totalCredited,
            BigDecimal totalRefunded,
            String currencyCode
    ) {}

    public record SellerLedgerView(
            String id,
            String transactionType,
            BigDecimal amount,
            String currencyCode,
            String orderId,
            Instant createdAt
    ) {}

    @Transactional(readOnly = true)
    public SellerBalanceSummary getSellerBalance(String sellerPublicId, Long requestingUserId, boolean isAdminOrFinance) {
        Seller seller = getAuthorizedSeller(sellerPublicId, requestingUserId, isAdminOrFinance);

        List<SellerLedgerEntry> entries = ledgerEntryRepository.findBySeller_IdOrderByCreatedAtDesc(seller.getId());

        BigDecimal balance = BigDecimal.ZERO;
        BigDecimal credited = BigDecimal.ZERO;
        BigDecimal refunded = BigDecimal.ZERO;

        for (SellerLedgerEntry entry : entries) {
            balance = balance.add(entry.getAmount());
            if (entry.getAmount().compareTo(BigDecimal.ZERO) > 0) {
                credited = credited.add(entry.getAmount());
            } else {
                refunded = refunded.add(entry.getAmount().abs());
            }
        }

        return new SellerBalanceSummary(
                seller.getPublicId(),
                seller.getName(),
                balance,
                credited,
                refunded,
                "PHP"
        );
    }

    @Transactional(readOnly = true)
    public List<SellerLedgerView> listLedgerEntries(String sellerPublicId, Long requestingUserId, boolean isAdminOrFinance) {
        Seller seller = getAuthorizedSeller(sellerPublicId, requestingUserId, isAdminOrFinance);

        return ledgerEntryRepository.findBySeller_IdOrderByCreatedAtDesc(seller.getId()).stream()
                .map(e -> new SellerLedgerView(
                        e.getPublicId().toString(),
                        e.getTransactionType(),
                        e.getAmount(),
                        e.getCurrencyCode(),
                        e.getSellerOrder() != null ? e.getSellerOrder().getPublicId().toString() : null,
                        e.getCreatedAt()
                ))
                .toList();
    }

    private Seller getAuthorizedSeller(String sellerPublicId, Long requestingUserId, boolean isAdminOrFinance) {
        Seller seller = sellerRepository.findByPublicId(sellerPublicId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Seller not found."));

        if (!isAdminOrFinance) {
            boolean isStaff = seller.getStaffMembers().stream()
                    .anyMatch(s -> s.getUser().getId().equals(requestingUserId));
            if (!isStaff) {
                throw new ApiException(ErrorCode.FORBIDDEN, "Not authorized to access financial data for this seller.");
            }
        }

        return seller;
    }
}
