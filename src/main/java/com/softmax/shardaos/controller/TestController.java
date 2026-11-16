package com.softmax.shardaos.controller;

import com.softmax.shardaos.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @Autowired
    private JwtUtil jwtUtil;

    // POST method (original)
    @PostMapping("/generate-token")
    public String generateTokenPost(@RequestParam Long userId) {
        return jwtUtil.generateToken(userId);
    }

    // GET method bhi add kar do (for easy testing)
    @GetMapping("/generate-token")
    public String generateTokenGet(@RequestParam Long userId) {
        return jwtUtil.generateToken(userId);
    }
}