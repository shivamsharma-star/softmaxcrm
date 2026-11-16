package com.softmax.shardaos.controller;

import com.softmax.shardaos.dto.output.ApiResponse;
import com.softmax.shardaos.dto.output.MyDetailsResponseDTO;
import com.softmax.shardaos.service.MyDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class MyDetailsController {

    @Autowired
    private MyDetailsService myDetailsService;

    @GetMapping("/api/getmydetails")
    public ResponseEntity<ApiResponse<MyDetailsResponseDTO>> getMyDetails(
            @RequestAttribute("userId") Long userId) {
        try {
            MyDetailsResponseDTO details = myDetailsService.getMyDetails(userId);
            return ResponseEntity.ok(ApiResponse.success(details));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }
}