package com.archimstechcorner.marketplace.features.customers.application;

import com.archimstechcorner.marketplace.features.customers.domain.model.CustomerAddress;
import com.archimstechcorner.marketplace.features.customers.domain.repository.CustomerAddressRepository;
import com.archimstechcorner.marketplace.shared.error.ApiException;
import com.archimstechcorner.marketplace.shared.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AddressService {

    private final CustomerAddressRepository addressRepository;

    public AddressService(CustomerAddressRepository addressRepository) {
        this.addressRepository = addressRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomerAddress> listAddresses(Long userId) {
        return addressRepository.findByUserId(userId);
    }

    @Transactional
    public CustomerAddress addAddress(Long userId, String type, String name, String phone, String line1, String line2, String city, String state, String postal, String country, boolean isDefault) {
        CustomerAddress address = CustomerAddress.create();
        address.setUserId(userId);
        address.setAddressType(type);
        address.setRecipientName(name);
        address.setPhone(phone);
        address.setLine1(line1);
        address.setLine2(line2);
        address.setCity(city);
        address.setState(state);
        address.setPostalCode(postal);
        address.setCountry(country);
        address.setDefault(isDefault);

        // If default, unset previous defaults for type
        if (isDefault) {
            addressRepository.findByUserIdAndIsDefaultTrueAndAddressType(userId, type).ifPresent(prev -> {
                prev.setDefault(false);
                addressRepository.saveAndFlush(prev);
            });
        }

        return addressRepository.save(address);
    }

    @Transactional(readOnly = true)
    public CustomerAddress getAddress(Long userId, String publicId) {
        CustomerAddress addr = addressRepository.findByPublicId(UUID.fromString(publicId))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Address not found."));
        if (!addr.getUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not your address.");
        }
        return addr;
    }

    @Transactional
    public CustomerAddress updateAddress(Long userId, String publicId, Long expectedVersion,
                                         String name, String phone, String line1, String line2,
                                         String city, String state, String postal, String country,
                                         Boolean isDefault) {
        CustomerAddress addr = getAddress(userId, publicId);
        if (expectedVersion != null && !expectedVersion.equals(addr.getVersion())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "Address was modified by another request. Stale version.");
        }

        if (name != null && !name.isBlank()) addr.setRecipientName(name);
        if (phone != null) addr.setPhone(phone);
        if (line1 != null && !line1.isBlank()) addr.setLine1(line1);
        if (line2 != null) addr.setLine2(line2);
        if (city != null && !city.isBlank()) addr.setCity(city);
        if (state != null) addr.setState(state);
        if (postal != null && !postal.isBlank()) addr.setPostalCode(postal);
        if (country != null && !country.isBlank()) addr.setCountry(country);

        if (isDefault != null && isDefault && !addr.isDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrueAndAddressType(userId, addr.getAddressType())
                    .ifPresent(prev -> {
                        if (!prev.getId().equals(addr.getId())) {
                            prev.setDefault(false);
                            addressRepository.saveAndFlush(prev);
                        }
                    });
            addr.setDefault(true);
        } else if (isDefault != null && !isDefault) {
            addr.setDefault(false);
        }

        return addressRepository.save(addr);
    }

    @Transactional
    public void deleteAddress(Long userId, String publicId, Long expectedVersion) {
        CustomerAddress addr = getAddress(userId, publicId);
        if (expectedVersion != null && !expectedVersion.equals(addr.getVersion())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "Address was modified by another request. Stale version.");
        }
        addressRepository.delete(addr);
    }

    @Transactional
    public CustomerAddress setDefault(Long userId, String publicId, Long expectedVersion) {
        CustomerAddress addr = getAddress(userId, publicId);
        if (expectedVersion != null && !expectedVersion.equals(addr.getVersion())) {
            throw new ApiException(ErrorCode.IDEMPOTENCY_CONFLICT, "Address was modified by another request. Stale version.");
        }
        if (!addr.isDefault()) {
            addressRepository.findByUserIdAndIsDefaultTrueAndAddressType(userId, addr.getAddressType())
                    .ifPresent(prev -> {
                        if (!prev.getId().equals(addr.getId())) {
                            prev.setDefault(false);
                            addressRepository.saveAndFlush(prev);
                        }
                    });
            addr.setDefault(true);
            return addressRepository.save(addr);
        }
        return addr;
    }
}
