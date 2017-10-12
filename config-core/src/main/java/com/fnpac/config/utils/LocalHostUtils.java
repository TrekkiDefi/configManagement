package com.fnpac.config.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Set;

/**
 * Created by 刘春龙 on 2017/10/12.
 */
public class LocalHostUtils {

    private static final Logger logger = LoggerFactory.getLogger(LocalHostUtils.class);

    public static final String SEPARATOR = "@";

    /**
     * 获取本机IP
     *
     * @return
     * @throws UnknownHostException
     */
    public static String getHostAddress() throws UnknownHostException {
        InetAddress addr = InetAddress.getLocalHost();
        return addr.getHostAddress();
    }

    public static String getSchemaPort(Boolean isSecure) {
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
                Boolean secure = (Boolean) mBeanServer.getAttribute(objectName, "secure");
                Boolean SSLEnabled = (Boolean) mBeanServer.getAttribute(objectName, "SSLEnabled");

                // [HTTP/1.1],[http],[false],[false]
                logger.info("[" + protocol + "],[" + scheme + "],[" + secure + "],[" + SSLEnabled + "]");

                if (SSLEnabled != null && SSLEnabled) {// tomcat6开始用SSLEnabled，SSLEnabled=true但secure未配置的情况
                    secure = true;
                    scheme = "https";
                }

                /*
                    <Connector connectionTimeout="20000" port="8080" protocol="HTTP/1.1" redirectPort="8443"/>

                    <Connector port="8443" protocol="org.apache.coyote.http11.Http11NioProtocol"
                       maxThreads="150" SSLEnabled="true" scheme="https" secure="true"
                       clientAuth="false" sslProtocol="TLS" />
                 */
                if (protocol != null && ("HTTP/1.1".equals(protocol) || protocol.contains("http"))) {
                    if (isSecure && "https".equals(scheme) && secure) {
                        return scheme + SEPARATOR + mBeanServer.getAttribute(objectName, "port");
                    } else if (!isSecure && !"https".equals(scheme) && !secure) {
                        return scheme + SEPARATOR + mBeanServer.getAttribute(objectName, "port");
                    }
                }
            }
        } catch (MalformedObjectNameException | AttributeNotFoundException | ReflectionException | InstanceNotFoundException | MBeanException e) {
            e.printStackTrace();
        }

        return null;
    }
}
