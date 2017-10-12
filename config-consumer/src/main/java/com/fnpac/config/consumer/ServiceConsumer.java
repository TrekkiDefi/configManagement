package com.fnpac.config.consumer;

import com.alibaba.fastjson.JSON;
import com.fnpac.config.core.APIInfo;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by 刘春龙 on 2017/10/9.
 */
public class ServiceConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ServiceConsumer.class);

    private String namespace = "webServiceCenter";
    private CuratorFramework client = null;

    private Map<String, Set<APIInfo>> services = new ConcurrentHashMap<>();
    private Map<String, Set<String>> servicesByP = new ConcurrentHashMap<>();

    /**
     * @param connectString zookeeper服务地址
     */
    public ServiceConsumer(String connectString) {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);// 连接重试策略
        client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(10000).retryPolicy(retryPolicy)
                .namespace(this.namespace).build();
        client.start();
    }

    /**
     * @param connectString zookeeper服务地址
     * @param namespace
     */
    public ServiceConsumer(String connectString, String namespace) {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);// 连接重试策略
        client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(10000).retryPolicy(retryPolicy)
                .namespace(!StringUtils.isEmpty(namespace) ? namespace : this.namespace).build();
        client.start();
    }

    /**
     * 获取服务
     *
     * @param bizCode 应用名
     * @return
     */
    public Map<String, Set<APIInfo>> getServices(String bizCode) {
        try {
            client.sync().forPath("/" + bizCode);// java.lang.Void 执行follower同步leader数据
            List<String> children = client.getChildren().forPath("/" + bizCode);
            logger.info("-----------services----------");

            if (children != null) {
                for (String bizChild : children) {
                    // 注册watcher，监听子节点创建、删除、数据更新
                    addChildWatcher("/" + bizCode + "/" + bizChild);

                    // TODO 暂不支持.do等后缀接口
                    String servicepath = bizChild.replace(".", "/");
                    if (!servicepath.startsWith("/"))
                        servicepath = "/" + servicepath;
                    if (!services.containsKey(servicepath))
                        services.put(servicepath, new HashSet<APIInfo>());

                    List<String> providers = client.getChildren().forPath("/" + bizCode + "/" + bizChild);
                    if (providers != null) {
                        for (String provider : providers) {

                            byte[] data = client.getData().forPath("/" + bizCode + "/" + bizChild + "/" + provider);

                            APIInfo apiInfo = null;
                            if (data != null && data.length > 0) {
                                try {
                                    apiInfo = JSON.parseObject(new String(data, "UTF-8"), APIInfo.class);
                                } catch (UnsupportedEncodingException e) {
                                    e.printStackTrace();
                                }
                            }
                            if (apiInfo == null) {
                                apiInfo = new APIInfo();
                            }
                            apiInfo.setIp(provider);

                            services.get(servicepath).add(apiInfo);// 填充服务ip
                            if (!servicesByP.containsKey(provider)) {
                                servicesByP.put(provider, new HashSet<String>());
                            }
                            servicesByP.get(provider).add(servicepath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return services;
    }

    /**
     * 监听子节点创建、删除、数据更新
     *
     * @param path 监听的节点路径
     * @throws Exception
     */
    public void addChildWatcher(String path) throws Exception {
        final PathChildrenCache cache = new PathChildrenCache(this.client,
                path, true);// 子节点创建、删除、数据更新监听watcher
        cache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);// 初始化完成，发送INITIALIZED事件
        cache.getListenable().addListener(new PathChildrenCacheListener() {

            /**
             * Called when a change has occurred
             *
             * @param client the client
             * @param event  describes the change
             * @throws Exception errors
             */
            @Override
            public void childEvent(CuratorFramework client, PathChildrenCacheEvent event) throws Exception {
                if (event.getType().equals(PathChildrenCacheEvent.Type.INITIALIZED)) {
                    System.out.println("客户端子节点cache初始化数据完成");
                } else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_ADDED)) {
                    updateLocalService(event.getData().getPath(), event.getData().getData(), 0);
                } else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)) {
                    updateLocalService(event.getData().getPath(), event.getData().getData(), 1);
                } else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_UPDATED)) {
                    // TODO api的port、schema等信息更新
                }
            }
        });
    }

    private void updateLocalService(String path, byte[] data, int delOrAdd) {
        String nodes[] = path.split("/");
        String provider = nodes[nodes.length - 1];
        String servicepath = nodes[nodes.length - 2];

        servicepath = servicepath.replace(".", "/");
        if (!servicepath.startsWith("/"))
            servicepath = "/" + servicepath;

        if (delOrAdd == 0) {// add
            // 创建节点
            if (!services.containsKey(servicepath)) {
                services.put(servicepath, new HashSet<APIInfo>());
            }

            APIInfo apiInfo = null;
            if (data != null && data.length > 0) {
                try {
                    apiInfo = JSON.parseObject(new String(data, "UTF-8"), APIInfo.class);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            if (apiInfo == null) {
                apiInfo = new APIInfo();
            }
            apiInfo.setIp(provider);

            services.get(servicepath).add(apiInfo);

            if (!servicesByP.containsKey(provider)) {
                servicesByP.put(provider, new HashSet<String>());
            }
            servicesByP.get(provider).add(servicepath);
        } else if (delOrAdd == 1) {
            //删除节点
            services.get(servicepath).remove(provider);
            servicesByP.remove(provider);
        }
    }

    public void printService() {
        Set<String> srs = services.keySet();
        for (String ss : srs) {
            System.out.println("service list ------- " + ss);
            Set<APIInfo> vals = services.get(ss);
            for (APIInfo val : vals) {
                System.out.println("        --------" + val.toString());
            }
        }
        Set<String> ps = servicesByP.keySet();
        for (String ss : ps) {
            System.out.println("provider list ------- " + ss);
            Set<String> vals = servicesByP.get(ss);
            for (String val : vals) {
                System.out.println("        ----------" + val);
            }
        }
    }

    public String accessService(String bizCode, String api) {

        List<APIInfo> providerList;

        Set<APIInfo> providers = services.get(api);
        if (providers != null && providers.size() > 0) {
            providerList = new ArrayList<>();
            providerList.addAll(providers);
            Collections.shuffle(providerList);
            APIInfo apiInfo = providerList.get(0);
            String server = apiInfo.getSchema() + "://" + apiInfo.getIp() + ":" + apiInfo.getPort() + "/" + bizCode + api;
            logger.info("access server: " + server);
            return server;
        } else {
            logger.info("access server " + api + " 没有服务提供者");
            return null;
        }
    }
}
