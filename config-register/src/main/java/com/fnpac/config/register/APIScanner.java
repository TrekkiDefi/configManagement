package com.fnpac.config.register;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.springframework.util.Assert;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 * Created by liuchunlong on 2017/10/8.
 * <p>
 * API注册扫描器
 */
public class APIScanner {

    private static final Logger logger = Logger.getLogger(APIScanner.class.getName());

    private String scanPath = "";// 扫描的包名
    private String connectString = "";// zookeeper服务地址
    private String bizCode = "sampleweb";// 应用名

    private CuratorFramework client = null;

    public APIScanner(String scanPath, String connectString, String bizcode) {

        Assert.notNull(scanPath, "scanPath is Null");
        Assert.notNull(connectString, "connectString is Null");
        Assert.notNull(bizcode, "bizcode is Null");

        this.scanPath = scanPath;
        this.connectString = connectString;
        this.bizCode = bizcode;
        logger.info(scanPath + " -- " + connectString + " -- " + bizCode);
    }

    /**
     * 扫描注册服务
     */
    public void init() {
        try {

            logger.info("register begin ...");

            // 初始化zk客户端
            buildZkClient();
            registBiz();

            // 扫描所有action类和方法
            Set classes = getClasses(scanPath, true);
            if (classes == null || classes.isEmpty())
                return;


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建zookeeper客户端
     */
    private void buildZkClient() {
        RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);// 重试策略
        client = CuratorFrameworkFactory.builder().connectString(connectString)
                .sessionTimeoutMs(10000).retryPolicy(retryPolicy)
                .namespace("webServiceCenter").build();
        client.start();
    }

    /**
     * 注册bizCode服务
     */
    private void registBiz() {
        try {
            if (client.checkExists().forPath("/" + bizCode) == null) {
                client.create().creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT)
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath("/" + bizCode, (bizCode + "提供的服务列表").getBytes());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取指定包{@code packageName}下面所有被添加服务注解的类
     * <p>
     * 服务注解为{@link org.springframework.stereotype.Controller}
     * 和方法上的{@link org.springframework.web.bind.annotation.RequestMapping}
     *
     * @param packageName 扫描的包名
     * @param recursive   是否循环迭代
     * @return
     */
    private Set<Class<?>> getClasses(final String packageName, boolean recursive) {
        Set<Class<?>> classes = new LinkedHashSet<>();

        // 获取包的名字并进行替换
        final String packageDirName = packageName.replace('.', '/');

        Enumeration<URL> dirs = null;
        try {
            dirs = Thread.currentThread().getContextClassLoader()
                    .getResources(packageDirName);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 循环迭代下去
        while (dirs != null && dirs.hasMoreElements()) {
            try {
                // 获取下一个元素
                URL url = dirs.nextElement();

                // 得到协议的名称
                String protocol = url.getProtocol();

                if ("file".equals(protocol)) {// 以file的形式保存在服务器上
                    logger.info("scan path: " + url.getPath() + ", protocol: " + protocol);
                    // 获取包的物理路径
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                    // 以文件的方式扫描整个包下的文件 并添加到集合中
                    findAndAddClassesInPackageByFile(packageName, filePath,
                            recursive, classes);
                } else if ("jar".equals(protocol)) {// 以jar的形式保存在服务器上
                    logger.info("scan path: " + url.getPath() + ", protocol: " + protocol);
                    // 获取jar
                    JarFile jar = ((JarURLConnection) url.openConnection())
                            .getJarFile();
                    // 从此jar包 得到一个枚举类
                    Enumeration<JarEntry> entries = jar.entries();
                    // 同样的进行循环迭代
                    while (entries.hasMoreElements()) {
                        // 获取jar里的一个文件，可能是目录或一些其他文件，如META-INF等文件。
                        JarEntry entry = entries.nextElement();
                        String entryName = entry.getName();

                        if (entryName.startsWith("/")) {// 如果以"/"开头，则删除"/"
                            entryName = entryName.substring(1);
                        }

                        // 如果是一个.class文件，而不是目录，且在扫描的packageName包下
                        if (entryName.endsWith(".class") && !entry.isDirectory()
                                && entryName.startsWith(packageDirName)) {

                            int idx = entryName.lastIndexOf('/');
                            // 扫描packageName包下的类，或者迭代扫描
                            if ((idx != -1 && idx == packageDirName.length()) || recursive) {
                                // 去掉后面的".class"，获取真正的类名，把"/"替换成"."
                                String className = entryName.substring(0,
                                        entryName.length() - 6)
                                        .replace('/', '.');
                                try {
                                    // 添加到classes
                                    classes.add(Thread.currentThread().getContextClassLoader()
                                            .loadClass(className));
                                } catch (ClassNotFoundException e) {
                                    e.printStackTrace();
                                }
                            }
                        }

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        return classes;
    }

    private void findAndAddClassesInPackageByFile(String packageName, String packagePath,
                                                  final boolean recursive, Set<Class<?>> classes) {
        // 获取此包的目录，建立一个File
        File dir = new File(packagePath);
        // 如果不存在，或者不是目录就直接返回
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        // 如果存在就获取包下的所有文件，包括目录
        File[] dirfiles = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                // 自定义过滤规则，如果可以循环(且包含子目录)或者是以.class结尾的文件(编译好的java类文件)
                return false;
            }
        });
    }
}
