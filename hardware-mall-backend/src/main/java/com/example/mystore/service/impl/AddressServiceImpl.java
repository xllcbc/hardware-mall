package com.example.mystore.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.mystore.entity.db.Address;
import com.example.mystore.entity.db.User;
import com.example.mystore.mapper.AddressMapper;
import com.example.mystore.mapper.UserMapper;
import com.example.mystore.service.AddressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final AddressMapper addressMapper;
    private final UserMapper userMapper;

    @Override
    public List<Address> getAddressList(Long userId) {
        LambdaQueryWrapper<Address> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Address::getUserId, userId)
               .eq(Address::getDeleteTime, 0)
               .orderByDesc(Address::getIsDefault)
               .orderByDesc(Address::getCreateTime);
        return addressMapper.selectList(wrapper);
    }

    @Override
    public Address getAddressById(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new RuntimeException("地址不存在");
        }
        if (address.getDeleteTime() != 0) {
            throw new RuntimeException("地址不存在");
        }
        return address;
    }

    @Override
    @Transactional
    public Address createAddress(Long userId, Address address) {
        address.setUserId(userId);
        address.setCreateTime(LocalDateTime.now());
        address.setUpdateTime(LocalDateTime.now());
        address.setDeleteTime(0L);
        
        if (address.getIsDefault() == null || address.getIsDefault() == 0) {
            address.setIsDefault(0);
        }
        
        if (address.getIsDefault() == 1) {
            clearDefaultAddress(userId);
        }

        addressMapper.insert(address);

        if (address.getIsDefault() == 1) {
            syncUserRegion(userId);
        }

        return address;
    }

    @Override
    @Transactional
    public Address updateAddress(Long userId, Long addressId, Address address) {
        Address exist = addressMapper.selectById(addressId);
        if (exist == null || !exist.getUserId().equals(userId)) {
            throw new RuntimeException("地址不存在");
        }
        
        if (address.getConsignee() != null) {
            exist.setConsignee(address.getConsignee());
        }
        if (address.getPhone() != null) {
            exist.setPhone(address.getPhone());
        }
        if (address.getProvince() != null) {
            exist.setProvince(address.getProvince());
        }
        if (address.getCity() != null) {
            exist.setCity(address.getCity());
        }
        if (address.getDistrict() != null) {
            exist.setDistrict(address.getDistrict());
        }
        if (address.getDetail() != null) {
            exist.setDetail(address.getDetail());
        }
        if (address.getPostalCode() != null) {
            exist.setPostalCode(address.getPostalCode());
        }
        if (address.getIsDefault() != null && address.getIsDefault() == 1) {
            clearDefaultAddress(userId);
            exist.setIsDefault(1);
        }

        exist.setUpdateTime(LocalDateTime.now());
        addressMapper.updateById(exist);

        if (exist.getIsDefault() == 1) {
            syncUserRegion(userId);
        }

        return exist;
    }

    @Override
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new RuntimeException("地址不存在");
        }
        if (address.getDeleteTime() != 0) {
            throw new RuntimeException("地址不存在");
        }

        boolean wasDefault = address.getIsDefault() == 1;

        address.setDeleteTime(System.currentTimeMillis());
        addressMapper.updateById(address);

        if (wasDefault) {
            syncUserRegion(userId);
        }
    }

    @Override
    @Transactional
    public void setDefaultAddress(Long userId, Long addressId) {
        Address address = addressMapper.selectById(addressId);
        if (address == null || !address.getUserId().equals(userId)) {
            throw new RuntimeException("地址不存在");
        }
        
        clearDefaultAddress(userId);

        address.setIsDefault(1);
        address.setUpdateTime(LocalDateTime.now());
        addressMapper.updateById(address);

        syncUserRegion(userId);
    }

    private void clearDefaultAddress(Long userId) {
        LambdaQueryWrapper<Address> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Address::getUserId, userId)
               .eq(Address::getIsDefault, 1);
        List<Address> defaultAddresses = addressMapper.selectList(wrapper);
        for (Address addr : defaultAddresses) {
            addr.setIsDefault(0);
            addr.setUpdateTime(LocalDateTime.now());
            addressMapper.updateById(addr);
        }
    }

    private void syncUserRegion(Long userId) {
        LambdaQueryWrapper<Address> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Address::getUserId, userId)
               .eq(Address::getIsDefault, 1)
               .eq(Address::getDeleteTime, 0);
        Address defaultAddress = addressMapper.selectOne(wrapper);

        if (defaultAddress != null) {
            LambdaUpdateWrapper<User> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(User::getId, userId)
                         .set(User::getProvince, defaultAddress.getProvince())
                         .set(User::getCity, defaultAddress.getCity());
            userMapper.update(null, updateWrapper);
        }
        // 无默认地址时不做任何修改
    }
}
