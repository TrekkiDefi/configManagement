package com.fnpac.config.register;

import com.alibaba.fastjson.JSON;
import com.fnpac.config.core.APIInfo;
import com.fnpac.config.utils.LocalHostUtils;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static com.fnpac.config.utils.LocalHostUtils.SEPARATOR;

/**
 * Created by liuchunlong on 2017/10/8.
 * <p>
 * API注册扫描器
 */
public class ServiceRegister {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(ServiceRegister.class);

    private String packageName = "";// 扫描的包名
    private String connectString = "";// zookeeper服务地址
    private String bizCode = "sampleweb";// 应用名

    private String namespace = "webServiceCenter";
    private String schema;// 注册的api的协议
    private String ip;// 注册的api的ip地址
    private Integer port;// 注册的api的端口
    private Boolean isSecure = true;// 是否是使用https协议

    private CuratorFramework client = null;

    /**
     * @param packageName   扫描的包名
     * @param connectString zookeeper服务地址
     * @param bizCode       应用名
     */
    protected ServiceRegister(String packageName, String connectString, String bizCode) {

        Assert.notNull(packageName, "packageName is Null");
        Assert.notNull(connectString, "connectString is Null");
        Assert.notNull(bizCode, "bizcode is Null");

        this.packageName = packageName;
        this.connectString = connectString;
        this.bizCode = bizCode;
        logger.info(packageName + " -- " + connectString + " -- " + bizCode);
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public void setSecure(Boolean secure) {
        isSecure = secure;
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
            Set classes = getClasses(packageName, true);
            if (classes == null || classes.isEmpty())
                return;
            // 通过注解得到服务地址
            List<String> services = getServicePath(classes);

            for (String s : services)
                logger.info("service: " + s);

            //把服务注册到zk
            registBizServices(services);
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
                .namespace(namespace).build();
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
     * @param recursive   是否递归
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

        // 循环递归
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
                    // 以文件的方式扫描整个包下的类，并添加到集合中
                    findAndAddClassesInPackageByFile(packageName, filePath,
                            recursive, classes);
                } else if ("jar".equals(protocol)) {// 以jar的形式保存在服务器上
                    logger.info("scan path: " + url.getPath() + ", protocol: " + protocol);
                    // 获取jar
                    JarFile jar = ((JarURLConnection) url.openConnection())
                            .getJarFile();
                    // 从此jar包 得到一个枚举类
                    Enumeration<JarEntry> entries = jar.entries();
                    // 同样的进行循环
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
                            // 扫描packageName包下的类，或者递归扫描
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

    /**
     * 以文件的方式扫描整个包下的类，并添加到集合中
     *
     * @param packageName 包名
     * @param packagePath 包路径
     * @param recursive   是否递归扫描
     * @param classes     Class集合
     */
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
            public boolean accept(File file) {
                // 自定义过滤规则，如果可以循环(且包含子目录)或者是以.class结尾的文件(编译好的java类文件)
                return (file.isDirectory() && recursive) || (file.getName().endsWith(".class"));
            }
        });

        // 循环所有文件
        if (dirfiles != null) {
            for (File file : dirfiles) {
                // 如果是目录 则继续扫描
                if (file.isDirectory()) {
                    findAndAddClassesInPackageByFile(
                            packageName + "." + file.getName(),
                            file.getAbsolutePath(), recursive, classes);
                } else {
                    // 如果是java类文件 去掉后面的.class 只留下类名
                    String className = file.getName().substring(0,
                            file.getName().length() - 6);
                    try {
                        // 添加到集合中去
//                         classes.add(Class.forName(packageName + '.' + className));
                        // 经过回复同学的提醒，这里用forName有一些不好，会触发static方法，没有使用classLoader的load干净
                        classes.add(Thread.currentThread().getContextClassLoader()
                                .loadClass(packageName + '.' + className));
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private List<String> getServicePath(Set<Class> classes) {
        List<String> services = new ArrayList<>();
        StringBuffer pathBuffer;
        Annotation ann;

        if (classes != null) {
            for (Class cls : classes) {
                ann = cls.getAnnotation(Controller.class);// Controller
                if (ann == null)
                    continue;

                ann = cls.getAnnotation(RequestMapping.class);// RequestMapping
                String basePath = getRequestMappingPath(ann);

                Method ms[] = cls.getMethods();
                if (ms == null || ms.length == 0)
                    continue;

                for (Method m : ms) {
                    ann = m.getAnnotation(RequestMapping.class);
                    String mPath = getRequestMappingPath(ann);

                    if (mPath != null) {
                        pathBuffer = new StringBuffer();
                        if (!StringUtils.isEmpty(basePath))
                            pathBuffer.append(basePath).append("/");
                        pathBuffer.append(mPath);
                    } else
                        continue;

                    services.add(pathBuffer.toString());
                }
            }
        }
        return services;
    }

    private String getRequestMappingPath(Annotation ann) {
        if (ann == null)
            return null;
        else {
            RequestMapping rma = (RequestMapping) ann;
            String[] paths = rma.value();
            if (paths.length > 0) {
                String path = paths[0];
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                return path;
            } else
                return null;
        }
    }

    private void registBizServices(List<String> services) {
        try {

            if (StringUtils.isEmpty(this.ip)) {
                this.ip = LocalHostUtils.getHostAddress();
            }
            if (StringUtils.isEmpty(this.schema) || StringUtils.isEmpty(this.port)) {
                String schemaPort = LocalHostUtils.getSchemaPort(this.isSecure);
                Assert.notNull(schemaPort, "Get tomcat information failed, schema or port is Null");

                if (StringUtils.isEmpty(this.schema))
                    this.schema = schemaPort.split(SEPARATOR)[0];

                if (StringUtils.isEmpty(this.port))
                    this.port = Integer.valueOf(schemaPort.split(SEPARATOR)[1]);
            }

            for (String s : services) {
                String svNode = s.replace("/", ".");
                if (svNode.startsWith("."))
                    svNode = svNode.substring(1);

                // 如果接口节点不存在，创建接口节点
                if (client.checkExists().forPath("/" + bizCode + "/" + svNode) == null) {
                    client.create().creatingParentsIfNeeded()
                            .withMode(CreateMode.PERSISTENT)
                            .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                            .forPath("/" + bizCode + "/" + svNode, ("api").getBytes());
                }

                // 创建当前机器的临时会话节点
                APIInfo apiInfo = new APIInfo();
                apiInfo.setPort(this.port);
                apiInfo.setSchema(this.schema);
                client.create()
                        .creatingParentsIfNeeded()
                        .withMode(CreateMode.EPHEMERAL)// 临时会话节点
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        .forPath("/" + bizCode + "/" + svNode + "/" + this.ip, JSON.toJSONString(apiInfo).getBytes());
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
