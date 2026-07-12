package com.archimstechcorner.marketplace.features.cart.api;

import com.archimstechcorner.marketplace.features.cart.application.CartService;
import com.archimstechcorner.marketplace.features.cart.application.CartView;
import com.archimstechcorner.marketplace.features.cart.application.WishlistService;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Wishlist ("save for later") REST API. User-scoped, like the cart.
 * The wishlist references products (not variants) — the user picks a variant when moving to cart.
 */
@RestController
@RequestMapping("/api/v1/wishlist")
@PreAuthorize("isAuthenticated()")
public class WishlistController {

    private final WishlistService wishlistService;
    private final CartService cartService;

    public WishlistController(WishlistService wishlistService, CartService cartService) {
        this.wishlistService = wishlistService;
        this.cartService = cartService;
    }

    /** Get the user's full wishlist. */
    @GetMapping
    public List<WishlistService.WishlistView> getWishlist(
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return wishlistService.getWishlist(principal.userId());
    }

    /** Add a product to the wishlist. Idempotent. */
    @PostMapping("/items")
    public List<WishlistService.WishlistView> addItem(
            @Valid @RequestBody AddToWishlistRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return wishlistService.addToWishlist(principal.userId(), request.productPublicId());
    }

    /** Remove a product from the wishlist. */
    @DeleteMapping("/items/{productPublicId}")
    public List<WishlistService.WishlistView> removeItem(
            @PathVariable String productPublicId,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        return wishlistService.removeFromWishlist(principal.userId(), productPublicId);
    }

    /**
     * Move a wishlisted product to the cart. The user must specify which variant to add.
     * The product is removed from the wishlist and the chosen variant is added to the cart.
     */
    @PostMapping("/items/{productPublicId}/move-to-cart")
    public MoveToCartResponse moveToCart(
            @PathVariable String productPublicId,
            @Valid @RequestBody MoveToCartRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        List<WishlistService.WishlistView> wishlist =
                wishlistService.removeFromWishlist(principal.userId(), productPublicId);
        CartView cart = cartService.addToCart(principal.userId(), request.variantPublicId(), 1);
        return new MoveToCartResponse(cart, wishlist);
    }

    // ---- Request / Response DTOs ----

    public record AddToWishlistRequest(@NotBlank String productPublicId) {}

    public record MoveToCartRequest(@NotBlank String variantPublicId) {}

    public record MoveToCartResponse(
            CartView cart,
            List<WishlistService.WishlistView> wishlist
    ) {}
}
