package com.example.mystore.service;

import com.example.mystore.entity.db.Address;

import java.util.List;

public interface AddressService {
    List<Address> getAddressList(Long userId);
    Address getAddressById(Long userId, Long addressId);
    Address createAddress(Long userId, Address address);
    Address updateAddress(Long userId, Long addressId, Address address);
    void deleteAddress(Long userId, Long addressId);
    void setDefaultAddress(Long userId, Long addressId);
}
