package com.fnpac;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.net.*;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

@RunWith(SpringRunner.class)
@SpringBootTest
public class ConfigManagementApplicationTests {

    private static final Logger logger = LoggerFactory.getLogger(ConfigManagementApplicationTests.class);

    /**
     * 获取此{@code URL}的文件名。
     * <p>
     * 返回的文件部分将与getPath()相同，加上getQuery()的值，如果有的话。
     * 如果无Query部分，此方法和getPath()返回相同的结果。
     * <p>
     * [scan path]: /Users/liuchunlong/IdeaProjects/configManagement/web/target/classes/com/fnpac, [protocol]: file<br/>
     * [file]: /Users/liuchunlong/IdeaProjects/configManagement/web/target/classes/com/fnpac<br/>
     * <p>
     * [scan path]: file:/Users/liuchunlong/.m2/repository/org/springframework/spring-test/4.3.11.RELEASE/spring-test-4.3.11.RELEASE.jar!/org/springframework/test, [protocol]: jar<br/>
     * [file]: file:/Users/liuchunlong/.m2/repository/org/springframework/spring-test/4.3.11.RELEASE/spring-test-4.3.11.RELEASE.jar!/org/springframework/test<br/>
     * [jar name]: /Users/liuchunlong/.m2/repository/org/springframework/spring-test/4.3.11.RELEASE/spring-test-4.3.11.RELEASE.jar<br/>
     * <p>
     * [jar inner name]: META-INF/<br/>
     * [jar inner name]: META-INF/MANIFEST.MF<br/>
     * ...
     * [jar inner name]: org/springframework/test/annotation/<br/>
     * [jar inner name]: org/springframework/test/annotation/DirtiesContext$MethodMode.class<br/>
     * ...
     */
    @Test
    public void scanTest() {

        String packageName = "org.springframework.test.web";
        boolean recursive = true;

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
                    logger.info("\n[scan path]: " + url.getPath() + ", [protocol]: " + protocol + "\n");
                    logger.info("\n[file]: " + url.getFile() + "\n");
                    // 获取包的物理路径
                    String filePath = URLDecoder.decode(url.getFile(), "UTF-8");
                    // 以文件的方式扫描整个包下的文件 并添加到集合中
//                    findAndAddClassesInPackageByFile(packageName, filePath,
//                            recursive, classes);
                } else if ("jar".equals(protocol)) {// 以jar的形式保存在服务器上
                    logger.info("\n[scan path]: " + url.getPath() + ", [protocol]: " + protocol + "\n");
                    // 获取包的物理路径
                    logger.info("\n[file]: " + url.getFile() + "\n");
                    JarFile jar = ((JarURLConnection) url.openConnection())
                            .getJarFile();
                    logger.info("\n[jar name]: " + jar.getName() + "\n");
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

//                        logger.info("[jar inner name]: " + entryName);
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
                                logger.info("\n[className]: " + className + "\n");
//                                try {
//                                    // 添加到classes
//                                    classes.add(Thread.currentThread().getContextClassLoader()
//                                            .loadClass(className));
//                                } catch (ClassNotFoundException e) {
//                                    e.printStackTrace();
//                                }
                            }
                        }

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 获取本机ip
     *
     * @throws UnknownHostException
     */
    @Test
    public void ipTest() throws UnknownHostException {
        InetAddress addr = InetAddress.getLocalHost();
        String ip = addr.getHostAddress();
        logger.info(ip);
    }
}
