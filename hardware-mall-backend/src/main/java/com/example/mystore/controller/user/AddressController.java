package com.example.mystore.controller.user;

import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.Address;
import com.example.mystore.service.AddressService;
import com.example.mystore.util.UserContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/user/address")
@RequiredArgsConstructor
@Slf4j
public class AddressController {

    private final AddressService addressService;

    @GetMapping("/list")
    public Result<List<Address>> getAddressList() {
        Long userId = UserContext.getUserId();
        return Result.success(addressService.getAddressList(userId));
    }

    @GetMapping("/{id}")
    public Result<Address> getAddressById(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        return Result.success(addressService.getAddressById(userId, id));
    }

    @PostMapping
    public Result<Address> createAddress(@Valid @RequestBody Address address) {
        Long userId = UserContext.getUserId();
        return Result.success(addressService.createAddress(userId, address));
    }

    @PutMapping("/{id}")
    public Result<Address> updateAddress(@PathVariable Long id, @Valid @RequestBody Address address) {
        Long userId = UserContext.getUserId();
        return Result.success(addressService.updateAddress(userId, id, address));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAddress(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        log.info("删除地址请求 | userId: {} | addressId: {}", userId, id);
        try {
            addressService.deleteAddress(userId, id);
            log.info("删除地址成功 | userId: {} | addressId: {}", userId, id);
            return Result.success();
        } catch (RuntimeException e) {
            log.error("删除地址业务异常 | userId: {} | addressId: {} | 错误: {}", userId, id, e.getMessage());
            throw e;
        }
    }

    @PutMapping("/{id}/default")
    public Result<Void> setDefaultAddress(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        addressService.setDefaultAddress(userId, id);
        return Result.success();
    }
}
