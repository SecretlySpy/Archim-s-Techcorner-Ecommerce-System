package com.archimstechcorner.marketplace.features.orders.application;

import com.archimstechcorner.marketplace.features.cart.application.CartView.CartItemView;
import com.archimstechcorner.marketplace.features.cart.application.CartService;
import com.archimstechcorner.marketplace.features.cart.application.CartView;
import com.archimstechcorner.marketplace.features.catalog.application.ProductService;
import com.archimstechcorner.marketplace.features.catalog.domain.model.Product;
import com.archimstechcorner.marketplace.features.catalog.domain.model.ProductVariant;
import com.archimstechcorner.marketplace.features.catalog.domain.repository.ProductVariantRepository;
import com.archimstechcorner.marketplace.features.customers.domain.model.CustomerAddress;
import com.archimstechcorner.marketplace.features.customers.domain.repository.CustomerAddressRepository;
import com.archimstechcorner.marketplace.features.orders.domain.model.CheckoutQuote;
import com.archimstechcorner.marketplace.features.orders.domain.repository.CheckoutQuoteRepository;
import com.archimstechcorner.marketplace.features.users.domain.model.User;
import com.archimstechcorner.marketplace.features.users.domain.repository.UserRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class CheckoutQuoteService {

    private final CheckoutQuoteRepository quoteRepository;
    private final CartService cartService;
    private final CustomerAddressRepository addressRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductService productService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public CheckoutQuoteService(CheckoutQuoteRepository quoteRepository,
                                CartService cartService,
                                CustomerAddressRepository addressRepository,
                                ProductVariantRepository variantRepository,
                                ProductService productService,
                                UserRepository userRepository,
                                ObjectMapper objectMapper) {
        this.quoteRepository = quoteRepository;
        this.cartService = cartService;
        this.addressRepository = addressRepository;
        this.variantRepository = variantRepository;
        this.productService = productService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public record QuoteItemSnapshot(
            Long variantId,
            String variantPublicId,
            String sku,
            String productName,
            String variantName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            Long sellerId,
            String sellerName
    ) {}

    public record QuoteResult(
            String quoteId,
            BigDecimal subtotal,
            BigDecimal shippingFee,
            BigDecimal taxAmount,
            BigDecimal totalAmount,
            String currency,
            Instant expiresAt,
            List<QuoteItemSnapshot> items
    ) {}

    /**
     * G01/G11: Creates a durable server-calculated checkout quote with a 15-minute expiration.
     * All prices and eligibility are verified from the database, never trusted from client inputs.
     */
    @Transactional
    public QuoteResult generateQuote(Long userId, String shippingAddressPublicId, String shippingMethod) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "User not found."));

        UUID addressUuid;
        try {
            addressUuid = UUID.fromString(shippingAddressPublicId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid shipping address ID format.");
        }

        CustomerAddress address = addressRepository.findByPublicId(addressUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Shipping address not found."));
        if (!address.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Address does not belong to the authenticated user.");
        }

        CartView cart = cartService.getCart(userId);
        if (cart.totalItems() == 0) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Cart is empty.");
        }

        List<QuoteItemSnapshot> snapshots = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (var sellerGroup : cart.sellerGroups()) {
            for (CartItemView item : sellerGroup.items()) {
                UUID variantUuid = UUID.fromString(item.variantPublicId());
                ProductVariant variant = variantRepository.findByPublicId(variantUuid)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Product variant not found: " + item.variantPublicId()));

                Product product = variant.getProduct();
                if (!productService.isEligibleForSale(product, variant)) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            "Item is not eligible for sale: " + product.getName() + " (" + variant.getName() + ")");
                }

                BigDecimal authoritativeUnitPrice = variant.getEffectivePrice();
                BigDecimal lineTotal = authoritativeUnitPrice.multiply(BigDecimal.valueOf(item.quantity()));
                subtotal = subtotal.add(lineTotal);

                snapshots.add(new QuoteItemSnapshot(
                        variant.getId(),
                        variant.getPublicId().toString(),
                        variant.getSku(),
                        product.getName(),
                        variant.getName(),
                        authoritativeUnitPrice,
                        item.quantity(),
                        lineTotal,
                        product.getSeller().getId(),
                        product.getSeller().getName()
                ));
            }
        }

        BigDecimal shippingFee = "express".equalsIgnoreCase(shippingMethod) ?
                new BigDecimal("300.00") : new BigDecimal("150.00");
        BigDecimal taxAmount = BigDecimal.ZERO; // Taxes included in Philippine standard retail pricing
        BigDecimal totalAmount = subtotal.add(shippingFee).add(taxAmount);
        Instant expiresAt = Instant.now().plus(15, ChronoUnit.MINUTES);

        String itemsJson;
        try {
            itemsJson = objectMapper.writeValueAsString(snapshots);
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to serialize quote items snapshot.");
        }

        CheckoutQuote quote = CheckoutQuote.create(
                user,
                address.getId(),
                subtotal,
                shippingFee,
                taxAmount,
                totalAmount,
                "PHP",
                itemsJson,
                expiresAt
        );

        quoteRepository.save(quote);

        return new QuoteResult(
                quote.getPublicId().toString(),
                subtotal,
                shippingFee,
                taxAmount,
                totalAmount,
                "PHP",
                expiresAt,
                snapshots
        );
    }

    @Transactional(readOnly = true)
    public CheckoutQuote requireActiveQuote(String quotePublicId, Long userId) {
        UUID quoteUuid;
        try {
            quoteUuid = UUID.fromString(quotePublicId);
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Invalid quote ID format.");
        }

        CheckoutQuote quote = quoteRepository.findByPublicId(quoteUuid)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout quote not found."));

        if (!quote.getUser().getId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Quote does not belong to the authenticated user.");
        }
        if (quote.isUsed()) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "This checkout quote has already been used.");
        }
        if (quote.isExpired()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Checkout quote has expired. Please refresh your cart.");
        }
        return quote;
    }
}
