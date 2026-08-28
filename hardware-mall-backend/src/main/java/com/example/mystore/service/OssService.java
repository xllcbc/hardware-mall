package com.example.mystore.service;

import org.springframework.web.multipart.MultipartFile;

public interface OssService {
    String uploadFile(MultipartFile file, String dir);

    void deleteFile(String url);
}
