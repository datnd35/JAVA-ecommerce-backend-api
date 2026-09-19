package com.myshop.controller;

import org.springframework.web.bind.annotation.*;

public class TestController {

    @GetMapping("/test")
    public String test() {
        return "Test successful!";
    }
}
