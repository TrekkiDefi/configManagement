package com.fnpac.config;

import com.fnpac.config.register.ServiceRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.Set;

/**
 * Created by 刘春龙 on 2017/10/10.
 */
@Configuration
public class WebConfig {

    private static final Logger logger = LoggerFactory.getLogger(WebConfig.class);

    public WebConfig() {
        getPort();
    }

    private Integer getPort() {
        Integer port = null;
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        if (mBeanServer == null) {
            logger.info("没有发现JVM中关联的MBeanServer.");
            return null;
        }

        try {
            Set<ObjectName> objectNames = mBeanServer.queryNames(new ObjectName("*:type=Connector,*"), null);
            if (objectNames == null || objectNames.size() == 0) {
                logger.info("没有发现JVM中关联的MBeanServer : "
                        + mBeanServer.getDefaultDomain() + " 中的对象名称.");
                return null;
            }
            for (ObjectName objectName : objectNames) {
                String protocol = (String) mBeanServer.getAttribute(objectName, "protocol");
                String scheme = (String) mBeanServer.getAttribute(objectName, "scheme");
                Boolean secureValue = (Boolean) mBeanServer.getAttribute(objectName, "secure");
                Boolean SSLEnabled = (Boolean) mBeanServer.getAttribute(objectName, "SSLEnabled");

                logger.info("[" + protocol + "],[" + scheme + "],[" + secureValue + "],[" + SSLEnabled + "],");
            }
        } catch (MalformedObjectNameException | AttributeNotFoundException | ReflectionException | InstanceNotFoundException | MBeanException e) {
            e.printStackTrace();
        }
        return port;
    }

    @Bean(initMethod = "init")
    public ServiceRegister serviceRegister() {

        return new ServiceRegister("com.fnpac", "localhost:2181",
                "configManagement", 8080);
    }
}
