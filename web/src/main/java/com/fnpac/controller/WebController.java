package com.fnpac.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Created by 刘春龙 on 2017/10/10.
 */
@Controller
@RequestMapping("/web")
public class WebController {

    @RequestMapping("/index")
    @ResponseBody
    public String index() {
        return "{\"msg\":\"hello world!~\"}";
    }
}
