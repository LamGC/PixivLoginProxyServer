package net.lamgc.plps;

import com.github.monkeywie.proxyee.crt.CertUtil;
import com.github.monkeywie.proxyee.proxy.ProxyConfig;
import com.github.monkeywie.proxyee.proxy.ProxyType;
import com.github.monkeywie.proxyee.server.HttpProxyCACertFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Main {

    private final static Logger log = LoggerFactory.getLogger("ApplicationMain");

    private final static Properties properties = new Properties();

    /**
     * PixivLoginProxyServer独立运行主方法.
     * 请不要通过本方法使用PixivLoginProxyServer，本方法将会在执行完毕时直接退出({@link System#exit(int)})程序
     * @deprecated 如需使用PixivLoginProxyServer请查看 {@link PixivLoginProxyServer}
     */
    @Deprecated
    public static void main(String[] args) {
        File propertiesFile = new File("./plps.properties");
        InputStream propInputStream;
        initCaCert();
        if(!propertiesFile.exists() || !propertiesFile.isFile()){
            propInputStream = ClassLoader.getSystemResourceAsStream("default.properties");
            if(propInputStream == null){
                log.error("初始配置文件创建失败!可能程序出现修改, 请重新下载或根据Readme创建配置文件." );
                System.exit(1);
                return;
            }
            try {
                log.info("正在导出初始配置文件...");
                Files.copy(propInputStream, propertiesFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("初始配置文件已导出, 请根据配置文件中的注释修改好配置文件.");
                System.exit(0);
            } catch (IOException e) {
                log.error("初始配置文件导出失败!", e);
            }
        } else {
            try {
                propInputStream = new FileInputStream(propertiesFile);
            } catch (FileNotFoundException e) {
                log.error("目录下配置文件读取失败!", e);
                System.exit(1);
                return;
            }
        }

        try {
            properties.load(propInputStream);
        } catch (IOException e) {
            log.error("读取配置文件失败!", e);
        }
        ProxyConfig forwardProxyConfig = null;
        HttpProxyCACertFactory caCertFactory = null;
        if(properties.containsKey("proxy.forwardProxy.host") && !properties.getProperty("proxy.forwardProxy.host").isEmpty()){
            if (!properties.containsKey("proxy.forwardProxy.type") || properties.getProperty("proxy.forwardProxy.type").isEmpty()) {
                log.warn("二级代理存在但配置不完整(缺少type), 将不会启用二级代理.");
            } else {
                ProxyType proxyType;
                try {
                    proxyType = ProxyType.valueOf(properties.getProperty("proxy.forwardProxy.type").toUpperCase());

                    String forwardProxyHost = properties.getProperty("proxy.forwardProxy.host");
                    String forwardProxyPortStr = properties.getProperty("proxy.forwardProxy.port", "1080");
                    int forwardProxyPort = forwardProxyPortStr.isEmpty() ? 1080 : Integer.parseInt(forwardProxyPortStr);
                    forwardProxyConfig = new ProxyConfig(proxyType, forwardProxyHost, forwardProxyPort);
                    log.info("已启用前置代理: {}", forwardProxyConfig);
                } catch (IllegalArgumentException e) {
                    log.warn("二级代理类型不支持, 当前仅支持Http/Socks4/Socks5代理服务器.");
                }
            }
        } else {
            log.debug("配置项未找到二级代理相关设置, 不启用二级代理");
        }

        //载入CA证书(如果有)
        File caCertFile = new File("./ca.crt");
        File caPrivateKeyFile = new File("./ca_private.der");
        if(caCertFile.exists() && caPrivateKeyFile.exists()) {
            try (FileInputStream caCertInput = new FileInputStream(caCertFile);
                 FileInputStream caPriKeyInput = new FileInputStream(caPrivateKeyFile)
            ) {
                caCertFactory = new InputStreamCertFactory(caCertInput, caPriKeyInput);
            } catch (IOException | CertificateException e) {
                log.error("读取外部CA证书失败", e);
            }
        }

        final PixivLoginProxyServer proxyServer = new PixivLoginProxyServer(forwardProxyConfig, caCertFactory);
        // Scanner 内部屏蔽了中断, 导致没办法停下主线程, 后续有空再改.
        // proxyServer.setLoginEventHandler(new AutoCloseHandler());
        Thread proxyServerStartThread = new Thread(() -> {
            log.info("Pixiv登录代理服务端启动中...");
            proxyServer.start(Integer.parseInt(properties.getProperty("proxy.loginProxyPort")));
            log.info("Pixiv登录代理服务端已关闭.");
        });
        proxyServerStartThread.setName("Thread-ProxyServerStart");
        proxyServerStartThread.start();

        Scanner commandScanner = new Scanner(System.in);
        for (; ; ) {
            String inputLine = commandScanner.nextLine();
            if ("close".equalsIgnoreCase(inputLine)) {
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
            log.warn("指定的保存位置({})已存在文件, 是否覆盖?[y/n](或任意输入取消)", storeFile.getAbsolutePath());
            String inputLine = commandScanner.nextLine();
            if (!inputLine.equalsIgnoreCase("y") && !inputLine.equalsIgnoreCase("yes")) {
                log.warn("操作已终止.");
                System.exit(0);
            }
        }

        saveCookieStore(storeFile, proxyServer);
    }

    private static void saveCookieStore(File storeFile, PixivLoginProxyServer proxyServer) {
        try {
            storeFile.delete();
            if (!storeFile.createNewFile()) {
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
            System.exit(1);
        }
    }

    private static void initCaCert() {
        File certFile = new File("./ca.crt");
        File privateKeyFile = new File("./ca_private.der");
        if (!certFile.exists() || !privateKeyFile.exists()) {
            certFile.delete();
            privateKeyFile.delete();

            try {
                KeyPair keyPair = CertUtil.genKeyPair();
                Files.write(Paths.get(certFile.toURI()),
                        CertUtil.genCACert(
                                "C=CN, ST=GD, L=SZ, O=lee, OU=study, CN=Proxyee",
                                new Date(),
                                new Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3650)),
                                keyPair)
                                .getEncoded());
                Files.write(privateKeyFile.toPath(),
                        new PKCS8EncodedKeySpec(keyPair.getPrivate().getEncoded()).getEncoded());
            } catch (Exception e) {
                log.error("创建CA证书时发生异常", e);
            }
        }
    }



}
