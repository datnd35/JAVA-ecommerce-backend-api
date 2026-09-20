package com.myshop.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myshop.common.ResultUtil;
import com.myshop.common.ResultUtil.ResultMessage;

@RestController
@RequestMapping("/test")
public class TestController {

    @GetMapping("/hello")
    public ResultMessage<String> test() {
        return ResultUtil.success("Test successful 123!");
    }
}