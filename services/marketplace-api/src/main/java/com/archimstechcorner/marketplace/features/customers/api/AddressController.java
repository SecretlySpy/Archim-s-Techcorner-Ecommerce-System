package com.archimstechcorner.marketplace.features.customers.api;

import com.archimstechcorner.marketplace.features.customers.application.AddressService;
import com.archimstechcorner.marketplace.features.customers.domain.model.CustomerAddress;
import com.archimstechcorner.marketplace.shared.security.AuthenticatedUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/addresses")
@PreAuthorize("isAuthenticated()")
public class AddressController {

    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return addressService.listAddresses(principal.userId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public AddressResponse create(@Valid @RequestBody CreateAddressRequest req, @AuthenticationPrincipal AuthenticatedUser principal) {
        CustomerAddress addr = addressService.addAddress(
                principal.userId(),
                req.addressType(),
                req.recipientName(),
                req.phone(),
                req.line1(),
                req.line2(),
                req.city(),
                req.state(),
                req.postalCode(),
                req.country(),
                req.isDefault()
        );
        return toResponse(addr);
    }

    @GetMapping("/{publicId}")
    public AddressResponse get(@PathVariable String publicId, @AuthenticationPrincipal AuthenticatedUser principal) {
        return toResponse(addressService.getAddress(principal.userId(), publicId));
    }

    @PutMapping("/{publicId}")
    public AddressResponse update(@PathVariable String publicId,
                                  @Valid @RequestBody UpdateAddressRequest req,
                                  @AuthenticationPrincipal AuthenticatedUser principal) {
        CustomerAddress addr = addressService.updateAddress(
                principal.userId(),
                publicId,
                req.expectedVersion(),
                req.recipientName(),
                req.phone(),
                req.line1(),
                req.line2(),
                req.city(),
                req.state(),
                req.postalCode(),
                req.country(),
                req.isDefault()
        );
        return toResponse(addr);
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String publicId,
                       @RequestParam(required = false) Long expectedVersion,
                       @AuthenticationPrincipal AuthenticatedUser principal) {
        addressService.deleteAddress(principal.userId(), publicId, expectedVersion);
    }

    @PutMapping("/{publicId}/default")
    public AddressResponse setDefault(@PathVariable String publicId,
                                      @RequestParam(required = false) Long expectedVersion,
                                      @AuthenticationPrincipal AuthenticatedUser principal) {
        return toResponse(addressService.setDefault(principal.userId(), publicId, expectedVersion));
    }

    private AddressResponse toResponse(CustomerAddress a) {
        return new AddressResponse(
                a.getPublicId().toString(),
                a.getVersion(),
                a.getAddressType(),
                a.getRecipientName(),
                a.getPhone(),
                a.getLine1(),
                a.getLine2(),
                a.getCity(),
                a.getState(),
                a.getPostalCode(),
                a.getCountry(),
                a.isDefault()
        );
    }

    public record CreateAddressRequest(
            @NotBlank String addressType,
            @NotBlank String recipientName,
            String phone,
            @NotBlank String line1,
            String line2,
            @NotBlank String city,
            String state,
            @NotBlank String postalCode,
            @NotBlank String country,
            boolean isDefault
    ) {}

    public record UpdateAddressRequest(
            Long expectedVersion,
            String recipientName,
            String phone,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country,
            Boolean isDefault
    ) {}

    public record AddressResponse(
            String id,
            Long version,
            String addressType,
            String recipientName,
            String phone,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country,
            boolean isDefault
    ) {}
}
