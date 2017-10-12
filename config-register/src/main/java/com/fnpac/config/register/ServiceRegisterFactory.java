package com.fnpac.config.register;

import org.springframework.util.StringUtils;

/**
 * Created by 刘春龙 on 2017/10/12.
 */
public class ServiceRegisterFactory {

    /**
     * 创建构造器
     *
     * @return
     */
    public static ServiceRegisterFactory.Builder builder() {
        return new ServiceRegisterFactory.Builder();
    }

    /**
     * 服务注册构造器
     */
    public static class Builder {

        private String packageName;// 扫描的包名
        private String connectString;// zookeeper服务地址
        private String bizCode;// 应用名

        private String namespace;
        private String schema;// 注册的api的协议
        private String ip;// 注册的api的ip地址
        private Integer port;// 注册的api的端口
        private Boolean isSecure;// 是否是使用https协议

        protected Builder() {
        }

        public Builder setNamespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder setConnectString(String connectString) {
            this.connectString = connectString;
            return this;
        }

        public Builder setBizCode(String bizCode) {
            this.bizCode = bizCode;
            return this;
        }

        public Builder setSchema(String schema) {
            this.schema = schema;
            return this;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public Builder setPort(Integer port) {
            this.port = port;
            return this;
        }

        public Builder setSecure(Boolean secure) {
            isSecure = secure;
            return this;
        }

        public ServiceRegister build() {
            ServiceRegister serviceRegister = new ServiceRegister(packageName, connectString, bizCode);
            if (!StringUtils.isEmpty(this.namespace)) {
                serviceRegister.setNamespace(this.namespace);
            }
            if (!StringUtils.isEmpty(this.schema)) {
                serviceRegister.setSchema(this.schema);
            }
            if (!StringUtils.isEmpty(this.ip)) {
                serviceRegister.setIp(this.ip);
            }
            if (this.port != null) {
                serviceRegister.setPort(this.port);
            }
            if (this.isSecure != null) {
                serviceRegister.setSecure(this.isSecure);
            }
            return serviceRegister;
        }
    }
}
