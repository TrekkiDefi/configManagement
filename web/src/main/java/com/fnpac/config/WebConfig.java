package com.fnpac.config;

import com.fnpac.config.register.ServiceRegister;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by 刘春龙 on 2017/10/10.
 */
@Configuration
public class WebConfig {

    @Bean(initMethod = "init")
    public ServiceRegister serviceRegister() {
        return new ServiceRegister("com.fnpac", "localhost:2181",
                "configManagement", 8080);
    }
}
