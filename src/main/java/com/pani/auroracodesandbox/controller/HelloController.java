package com.pani.auroracodesandbox.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Pani
 * @date Created in 2024/3/10 10:02
 * @description
 */
@RestController("/")
public class HelloController {
    @GetMapping("/hi")
    public String hi(){
        return "hello！！！！！！！";
    }
}
