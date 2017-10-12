package com.fnpac.config;

import com.fnpac.config.register.ServiceRegister;
import com.fnpac.config.register.ServiceRegisterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Created by 刘春龙 on 2017/10/10.
 */
@Configuration
public class WebConfig {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    @Bean(initMethod = "init")
    public ServiceRegister serviceRegister() {

        return ServiceRegisterFactory.builder().setPackageName("com.fnpac")
                .setConnectString("localhost:2181")
                .setBizCode("configManagement")
                .setSecure(false).build();

    }
}
