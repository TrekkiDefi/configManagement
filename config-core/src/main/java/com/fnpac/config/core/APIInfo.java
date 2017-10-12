package com.fnpac.config.core;

/**
 * Created by 刘春龙 on 2017/10/9.
 */
public class APIInfo {

    private String ip;// ip
    private Integer port;// 端口
    private String schema;// 协议

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public APIInfo() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        APIInfo apiInfo = (APIInfo) o;

        return ip != null ? ip.equals(apiInfo.ip) : apiInfo.ip == null;
    }

    @Override
    public int hashCode() {
        return ip != null ? ip.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "APIInfo{" +
                "ip='" + ip + '\'' +
                ", port=" + port +
                ", schema='" + schema + '\'' +
                '}';
    }
}
