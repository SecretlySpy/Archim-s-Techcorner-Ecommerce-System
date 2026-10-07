package com.archimstechcorner.marketplace.features.cart.api;

import com.archimstechcorner.marketplace.features.cart.application.CartService;
import com.archimstechcorner.marketplace.features.cart.application.CartView;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shopping cart REST API. All endpoints are user-scoped (the cart belongs to the authenticated
 * principal). No special permissions beyond authentication — ownership is implicit.
 *
 * <p>The cart is persistent in MySQL and supports items from multiple sellers. Items are
 * grouped by seller in the response for checkout preview.
 */
@RestController
@RequestMapping("/api/v1/cart")
@PreAuthorize("isAuthenticated()")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    /** Get the full cart with items grouped by seller, subtotals, and cart total. */
    @GetMapping
    public CartView getCart(@AuthenticationPrincipal AuthenticatedUser principal) {
        return cartService.getCart(principal.userId());
    }

    /** Add a variant to the cart (or increment quantity if already present). */
    @PostMapping("/items")
    @ResponseStatus(HttpStatus.OK)
    public CartView addItem(@Valid @RequestBody AddToCartRequest request,
                            @AuthenticationPrincipal AuthenticatedUser principal) {
        return cartService.addToCart(principal.userId(), request.variantPublicId(), request.quantity());
    }

    /** Set an explicit quantity for a cart item. If 0, the item is removed. */
    @PutMapping("/items/{variantPublicId}")
    public CartView updateItem(@PathVariable String variantPublicId,
                               @Valid @RequestBody UpdateQuantityRequest request,
                               @AuthenticationPrincipal AuthenticatedUser principal) {
        return cartService.updateQuantity(principal.userId(), variantPublicId, request.quantity());
    }

    /** Remove a specific item from the cart. */
    @DeleteMapping("/items/{variantPublicId}")
    public CartView removeItem(@PathVariable String variantPublicId,
                               @AuthenticationPrincipal AuthenticatedUser principal) {
        return cartService.removeFromCart(principal.userId(), variantPublicId);
    }

    /** Clear the entire cart. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearCart(@AuthenticationPrincipal AuthenticatedUser principal) {
        cartService.clearCart(principal.userId());
    }

    /** T11/T18 stub: Checkout - revalidates stock/price, creates intent (full orders in later). Returns confirmation. */
    @PostMapping("/checkout")
    public CheckoutResult checkout(@AuthenticationPrincipal AuthenticatedUser principal) {
        return cartService.checkout(principal.userId());
    }

    public record CheckoutResult(String orderId, String status, java.math.BigDecimal total, String paymentRedirect) {}

    // ---- Request DTOs ----

    public record AddToCartRequest(
            @NotBlank String variantPublicId,
            @Min(1) int quantity
    ) {}

    public record UpdateQuantityRequest(
            @Min(0) int quantity
    ) {}
}
