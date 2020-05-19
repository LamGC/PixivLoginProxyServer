package net.lamgc.plps;

import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptInitializer;
import com.github.monkeywie.proxyee.intercept.HttpProxyInterceptPipeline;
import com.github.monkeywie.proxyee.intercept.common.CertDownIntercept;
import com.github.monkeywie.proxyee.intercept.common.FullResponseIntercept;
import com.github.monkeywie.proxyee.proxy.ProxyConfig;
import com.github.monkeywie.proxyee.server.HttpProxyCACertFactory;
import com.github.monkeywie.proxyee.server.HttpProxyServer;
import com.github.monkeywie.proxyee.server.HttpProxyServerConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.http.client.CookieStore;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpCookie;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 由用户介入, 让用户手动登录Pixiv的方式, 再通过代理服务器捕获Cookie来绕过Google人机验证
 * @author LamGC
 */
public class PixivLoginProxyServer {

    private final Logger log = LoggerFactory.getLogger(this.getClass().getSimpleName());

    private boolean login = false;

    private final HttpProxyServer proxyServer;

    private final CookieStore cookieStore = new BasicCookieStore();

    private final AtomicReference<BooleanSupplier> loginEventHandler = new AtomicReference();

    public PixivLoginProxyServer() {
        this(null);
    }

    public PixivLoginProxyServer(ProxyConfig proxyConfig) {
        this(proxyConfig, null);
    }

    /**
     * 构造一个Pixiv登录会话代理服务端
     * @param proxyConfig 前置代理设置, 如为null则无前置代理
     * @param caCertFactory 自定义CA证书工厂对象, 如为null, 则使用Proxyee自带CA证书
     */
    public PixivLoginProxyServer(ProxyConfig proxyConfig, HttpProxyCACertFactory caCertFactory) {
        HttpProxyServerConfig config = new HttpProxyServerConfig();
        config.setHandleSsl(true);
        this.proxyServer = new HttpProxyServer();
        this.proxyServer
                .serverConfig(config)
                .proxyConfig(proxyConfig)
                .caCertFactory(caCertFactory)
                .proxyInterceptInitializer(new HttpProxyInterceptInitializer(){
                    @Override
                    public void init(HttpProxyInterceptPipeline pipeline) {
                        if(caCertFactory != null) {
                            log.debug("CertFactory存在，获取CA证书...");
                            try {
                                pipeline.addLast(new CaCertDownIntercept(caCertFactory.getCACert()));
                                log.debug("CaCertDownIntercept已使用CertFactory中的CA证书.");
                            } catch(Exception e) {
                                log.warn("从CertFactory获取CA证书时发生异常，将使用Proxyee自带证书.", e);
                                pipeline.addLast(new CertDownIntercept());
                            }
                        } else {
                            log.debug("CertFactory不存在, 使用Proxyee自带的CA证书.");
                            pipeline.addLast(new CertDownIntercept());
                        }

                        pipeline.addLast(new FullResponseIntercept() {
                            @Override
                            public boolean match(HttpRequest httpRequest, HttpResponse httpResponse, HttpProxyInterceptPipeline httpProxyInterceptPipeline) {
                                String host = httpRequest.headers().get(HttpHeaderNames.HOST);
                                return host.equalsIgnoreCase("pixiv.net") || host.contains(".pixiv.net");
                            }

                            @Override
                            public void handelResponse(HttpRequest httpRequest, FullHttpResponse fullHttpResponse, HttpProxyInterceptPipeline httpProxyInterceptPipeline) {
                                String url = httpRequest.headers().get(HttpHeaderNames.HOST) + httpRequest.uri();
                                log.info("拦截到Pixiv请求, URL: " + url);

                                log.info("正在导出Response Cookie...(Header Name: " + HttpHeaderNames.SET_COOKIE + ")");
                                List<String> responseCookies = fullHttpResponse.headers().getAll(HttpHeaderNames.SET_COOKIE);
                                AtomicInteger responseCookieCount = new AtomicInteger();
                                responseCookies.forEach(value -> {
                                    log.debug("Response Cookie: " + value);
                                    cookieStore.addCookie(parseRawCookie(value));
                                    responseCookieCount.incrementAndGet();
                                });
                                log.info("Cookie导出完成(已导出 " + responseCookieCount.get() + " 条Cookie)");

                                //登录检查
                                // 如果用户在登录界面登录成功后反复刷新，会出现登录返回不对但已经成功登录的情况，
                                // 故此处在登录完成后不再判断是否成功登录
                                if(!isLogin() && url.contains("accounts.pixiv.net/api/login")){
                                    log.info("正在检查登录结果...");
                                    //拷贝一份以防止对原响应造成影响
                                    FullHttpResponse copyResponse = fullHttpResponse.copy();
                                    ByteBuffer buffer = ByteBuffer.allocate(copyResponse.content().capacity());
                                    String contentStr;
                                    copyResponse.content().readBytes(buffer);
                                    contentStr = new String(buffer.array(), StandardCharsets.UTF_8);
                                    log.debug("Login Result: " + contentStr);

                                    JsonObject resultObject = new Gson().fromJson(contentStr, JsonObject.class);
                                    //只要error:false, body存在(应该是会存在的)且success字段存在, 即为登录成功
                                    login = !resultObject.get("error").getAsBoolean() &&
                                            resultObject.has("body") &&
                                            resultObject.get("body").getAsJsonObject().has("success");
                                    log.info("登录状态确认: " + (login ? "登录成功" : "登录失败"));

                                    fullHttpResponse.content().clear().writeBytes(
                                            ("{\"error\":false,\"message\":\"\",\"body\":{\"validation_errors\":{\"etc\":\"" +
                                                    StringEscapeUtils.escapeJava("Pixiv登录代理器已确认登录") + "\"}}}")
                                                    .getBytes(StandardCharsets.UTF_8));

                                    BooleanSupplier handler = loginEventHandler.get();
                                    if(handler != null) {
                                        boolean close = false;
                                        try {
                                            close = handler.getAsBoolean();
                                        } catch(Throwable e) {
                                            log.error("执行 LoginEventHandler 时发生异常", e);
                                        }
                                    }
                                }
                            }

                            protected BasicClientCookie parseRawCookie(String rawCookie) {
                                List<HttpCookie> cookies = HttpCookie.parse(rawCookie);
                                if (cookies.size() < 1)
                                    return null;
                                HttpCookie httpCookie = cookies.get(0);
                                BasicClientCookie cookie = new BasicClientCookie(httpCookie.getName(), httpCookie.getValue());
                                if (httpCookie.getMaxAge() >= 0) {
                                    Date expiryDate = new Date(System.currentTimeMillis() + httpCookie.getMaxAge() * 1000);
                                    cookie.setExpiryDate(expiryDate);
                                }
                                if (httpCookie.getDomain() != null)
                                    cookie.setDomain(httpCookie.getDomain());
                                if (httpCookie.getPath() != null)
                                    cookie.setPath(httpCookie.getPath());
                                if (httpCookie.getComment() != null)
                                    cookie.setComment(httpCookie.getComment());
                                cookie.setSecure(httpCookie.getSecure());
                                return cookie;
                            }
                        });
                    }
                });
    }

    /**
     * 启动代理服务端
     * @param port 代理端口号
     */
    public void start(int port){
        if(port > 65535 || port < 0) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        this.proxyServer.start(port);
    }

    /**
     * 关闭代理服务端
     */
    public void close(){
        this.proxyServer.close();
    }

    /**
     * 是否已登录Pixiv
     * @return 如已登录返回true
     */
    public boolean isLogin(){
        return login;
    }

    /**
     * 设置登录事件处理
     */
    public void setLoginEventHandler(BooleanSupplier handler) {
        loginEventHandler.set(handler);
    }

    /**
     * 导出CookieStore.
     * 注意!该方法导出的CookieStore不适用于ApacheHttpClient, 如需使用则需要进行转换.
     * @return CookieStore对象
     */
    public CookieStore getCookieStore(){
        return this.cookieStore;
    }

}
