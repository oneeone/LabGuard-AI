package com.labguard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * R1：站点入口统一跳到新建巡检页。
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "redirect:/inspections/new";
    }
}
