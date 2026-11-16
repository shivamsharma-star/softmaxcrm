package com.softmax.shardaos.dto.input;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewUserMailRequest {
    private String intrest;   // User's interest/
    private String msg;      //users text
}