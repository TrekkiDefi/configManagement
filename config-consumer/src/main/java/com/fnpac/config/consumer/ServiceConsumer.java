package com.fnpac.config.consumer;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Created by 刘春龙 on 2017/10/9.
 */
public class ServiceConsumer {

    private static final Logger logger = LoggerFactory.getLogger(ServiceConsumer.class);

    private CuratorFramework client = null;

    Map<String, Set<String>> services = new HashMap<String, Set<String>>();
    Map<String, Set<String>> servicesByIP = new HashMap<String, Set<String>>();

    /**
     * @param connectString zookeeper服务地址
     */
    public ServiceConsumer(String connectString) {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);// 连接重试策略
        client = CuratorFrameworkFactory.builder()
                .connectString(connectString)
                .sessionTimeoutMs(10000).retryPolicy(retryPolicy)
                .namespace("webServiceCenter").build();
        client.start();
    }

    /**
     * 获取服务
     *
     * @param bizCode 应用名
     * @return
     */
    public Map<String, Set<String>> getServices(String bizCode) {
        try {
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
                        services.put(servicepath, new HashSet<String>());

                    List<String> providers = client.getChildren().forPath("/" + bizCode + "/" + bizChild);
                    if (providers != null) {
                        for (String provider : providers) {
                            services.get(servicepath).add(provider);// 填充服务ip
                            if (!servicesByIP.containsKey(provider)) {
                                servicesByIP.put(provider, new HashSet<String>());
                            }
                            servicesByIP.get(provider).add(servicepath);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        printService();
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
                    updateLocalService(event.getData().getPath(), 0);
                } else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_REMOVED)) {
                    updateLocalService(event.getData().getPath(), 1);
                } else if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_UPDATED)) {
                }
            }
        });
    }

    private void updateLocalService(String path, int delOrAdd) {
        String nodes[] = path.split("/");
        String provider = nodes[nodes.length - 1];
        String servicepath = nodes[nodes.length - 2];

        servicepath = servicepath.replace(".", "/");
        if (!servicepath.startsWith("/"))
            servicepath = "/" + servicepath;

        if (delOrAdd == 0) {// add
            // 创建节点
            if (!services.containsKey(servicepath)) {
                services.put(servicepath, new HashSet<String>());
            }
            services.get(servicepath).add(provider);

            if (!servicesByIP.containsKey(provider)) {
                servicesByIP.put(provider, new HashSet<String>());
            }
            servicesByIP.get(provider).add(servicepath);
        } else if (delOrAdd == 1) {
            //删除节点
            services.get(servicepath).remove(provider);
            servicesByIP.remove(provider);
        }
        printService();
    }

    private void printService() {
        Set<String> srs = services.keySet();
        for (String ss : srs) {
            System.out.println("service list ------- " + ss);
            Set<String> vals = services.get(ss);
            for (String val : vals) {
                System.out.println("        --------" + val);
            }
        }
        Set<String> ps = servicesByIP.keySet();
        for (String ss : ps) {
            System.out.println("provider list ------- " + ss);
            Set<String> vals = servicesByIP.get(ss);
            for (String val : vals) {
                System.out.println("        ----------" + val);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        ServiceConsumer sc = new ServiceConsumer("localhost:2181");
        sc.getServices("userCenter");

        for (int i = 0; i < 10; i++) {
            sc.accessService("userCenter");
            Thread.sleep(10000);
        }
        Thread.sleep(1000000);
    }

    private void accessService(String bizCode) {
        Set<String> srs = services.keySet();
        List<String> providers;
        String server;
        for (String sr : srs) {
            if (services.get(sr) != null && services.get(sr).size() > 0) {
                providers = new ArrayList<>();
                providers.addAll(services.get(sr));
                Collections.shuffle(providers);
                // TODO 需要进一步优化，以支持端口的配置
                server = "http://" + providers.get(0) + ":8080/" + bizCode + sr;
                logger.info("access server: " + server);
            } else {
                System.out.println("access server " + sr + ",没有服务提供者");
            }
        }
    }
}
