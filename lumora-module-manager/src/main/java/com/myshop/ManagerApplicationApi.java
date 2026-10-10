package com.myshop;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.myshop.modules.product.mapper")
public class ManagerApplicationApi {

    public static void main(String[] args) {
        SpringApplication.run(ManagerApplicationApi.class, args);
    }
}