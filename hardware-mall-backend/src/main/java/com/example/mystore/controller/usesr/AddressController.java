package com.example.mystore.controller.usesr;

import com.example.mystore.common.result.Result;
import com.example.mystore.entity.db.Address;
import com.example.mystore.service.AddressService;
import com.example.mystore.util.JwtUtil;
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
    private final JwtUtil jwtUtil;

    @GetMapping("/list")
    public Result<List<Address>> getAddressList(@RequestHeader("Authorization") String authHeader) {
        Long userId = extractUserId(authHeader);
        return Result.success(addressService.getAddressList(userId));
    }

    @GetMapping("/{id}")
    public Result<Address> getAddressById(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long userId = extractUserId(authHeader);
        return Result.success(addressService.getAddressById(userId, id));
    }

    @PostMapping
    public Result<Address> createAddress(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Address address) {
        Long userId = extractUserId(authHeader);
        return Result.success(addressService.createAddress(userId, address));
    }

    @PutMapping("/{id}")
    public Result<Address> updateAddress(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id,
            @RequestBody Address address) {
        Long userId = extractUserId(authHeader);
        return Result.success(addressService.updateAddress(userId, id, address));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteAddress(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long userId = extractUserId(authHeader);
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
    public Result<Void> setDefaultAddress(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long id) {
        Long userId = extractUserId(authHeader);
        addressService.setDefaultAddress(userId, id);
        return Result.success();
    }

    private Long extractUserId(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.getUserIdFromToken(token);
        }
        throw new RuntimeException("无效的认证信息");
    }
}
