package net.lamgc.plps;

import com.github.monkeywie.proxyee.proxy.ProxyConfig;
import com.github.monkeywie.proxyee.proxy.ProxyType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Properties;
import java.util.Scanner;

public class Main {

    private final static Logger log = LoggerFactory.getLogger("ApplicationMain");

    private final static Properties properties = new Properties();

    public static void main(String[] args) {
        File propertiesFile = new File("./plps.properties");
        InputStream propInputStream;
        if(!propertiesFile.exists() || !propertiesFile.isFile()){
            propInputStream = ClassLoader.getSystemResourceAsStream("default.properties");
        } else {
            try {
                propInputStream = new FileInputStream(propertiesFile);
            } catch (FileNotFoundException e) {
                log.error("目录下配置文件读取失败!", e);
                propInputStream = ClassLoader.getSystemResourceAsStream("default.properties");
            }
        }

        try {
            properties.load(propInputStream);
        } catch (IOException e) {
            log.error("读取配置文件失败!", e);
        }
        PixivLoginProxyServer proxyServer;
        if(properties.containsKey("proxy.forwardProxy.host")){
            if(!properties.containsKey("proxy.forwardProxy.port")){
                log.warn("二级代理存在但配置不完整(缺少port), 将不会启用二级代理.");
            } else if(!properties.containsKey("proxy.forwardProxy.type")) {
                log.warn("二级代理存在但配置不完整(缺少type), 将不会启用二级代理.");
            }
            ProxyType proxyType = null;
            try {
                proxyType = ProxyType.valueOf(properties.getProperty("proxy.forwardProxy.type").toLowerCase());
            } catch (IllegalArgumentException e) {
                log.warn("二级代理类型不支持, 当前仅支持Http/Socks4/Socks5代理服务器.");
            }
            if(proxyType != null){
                proxyServer = new PixivLoginProxyServer(
                        new ProxyConfig(proxyType,
                                properties.getProperty("proxy.forwardProxy.host"),
                                Integer.parseInt(properties.getProperty("proxy.forwardProxy.port"))));
            } else {
                proxyServer = new PixivLoginProxyServer();
            }
        } else {
            log.debug("配置项未找到二级代理相关设置, 不启用二级代理");
            proxyServer = new PixivLoginProxyServer();
        }

        Thread proxyServerStartThread = new Thread(() -> {
            log.info("Pixiv登录代理服务端正在启动中...");
            proxyServer.start(Integer.parseInt(properties.getProperty("proxy.loginProxyPort")));
            log.info("Pixiv登录代理服务端已关闭.");
        });
        proxyServerStartThread.setName("Thread-ProxyServerStart");
        proxyServerStartThread.start();

        Scanner commandScanner = new Scanner(System.in);
        for(;;){
            String inputLine = commandScanner.nextLine();
            if(inputLine.equalsIgnoreCase("close")){
                log.info("正在关闭Pixiv登录代理服务端...");
                proxyServer.close();
                break;
            } else {
                log.warn("要退出并保存CookieStore, 请输入\"close\"");
            }
        }

        log.info("正在保存CookieStore...");
        File storeFile = new File(properties.getProperty("proxy.cookieStorePath", "./cookies.store"));
        if(storeFile.exists()){
            log.warn("指定的保存位置({})已存在文件, 是否覆盖?[y/n]", storeFile.getAbsolutePath());
            String inputLine = commandScanner.nextLine();
            if(inputLine.equalsIgnoreCase("n") || inputLine.equalsIgnoreCase("no")){
                log.warn("操作已终止.");
                System.exit(0);
            }
        }

        try {
            storeFile.delete();
            if(!storeFile.createNewFile()){
                log.error("文件创建失败!(Path: {})", storeFile.getAbsolutePath());
                System.exit(1);
            }

            ObjectOutputStream outputStream = new ObjectOutputStream(new FileOutputStream(storeFile));
            outputStream.writeObject(proxyServer.getCookieStore());
            outputStream.flush();
            outputStream.close();
            log.info("CookieStore保存完成.(Path: {})", storeFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("CookieStore保存失败!", e);
        }

        try {
            if(!propertiesFile.exists() && !propertiesFile.createNewFile()){
                log.error("配置文件保存失败!(文件创建失败)");
                System.exit(1);
            }
            properties.store(new FileOutputStream(propertiesFile), "PixivLoginProxyServer setting");
        } catch (IOException e) {
            log.error("配置文件保存失败!", e);
            System.exit(1);
        }
    }

}
