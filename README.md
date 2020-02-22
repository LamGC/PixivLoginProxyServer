# PixivLoginProxyServer
A pixiv crawler login program for deceiving reCAPTCHA, applicable to Java related crawlers, but welcome to extend!
## 配置文件 ##
当缺少配置文件时, 将会使用`default.properties`:
```properties
#二级代理选填
#二级代理类型, 目前仅支持http/socks4/socks5
proxy.forwardProxy.type=http
#二级代理服务器地址
proxy.forwardProxy.host=
#二级代理服务器端口
proxy.forwardProxy.port=
#Pixiv用户登录代理端口
proxy.loginProxyPort=1081
#CookieStore导出路径
proxy.cookieStorePath=cookies.store
```
第一次运行后将会导出`plps.properties`文件，请根据注释修改值。

## 如何使用 ##
### 使用PixivLoginProxyServer登录Pixiv获得CookieStore ###
第一次运行时, 由于Pixiv使用了Https加密了访问，所以请打开`127.0.0.1:{proxyPort}`以下载CA证书并进行导入，   
导入时，请将证书导入到`受信任的根证书颁发机构`，导入成功即可。  
第一次运行不存在配置文件，程序将会初始化配置文件并提示修改配置文件，更改好配置文件后启动程序，并设置浏览器代理，  
使用浏览器登录Pixiv时推荐使用浏览器隐身模式登录，可以不需要退出浏览器已登录的Pixiv，更方便的登录Pixiv。  
登录后输入`close`回车即可，如上次存在导出的CookieStore，则按情况选择是否覆盖。

### 其他程序导入PixivLoginProxyServer导出的CookieStore ###
PixivLoginProxyServer所采用的CookieStore来自`org.apache.http.client.CookieStore`，  
导入时仅需要使用`ObjectInputStream`读取后转换即可。

## LICENSE ##
本项目采用`Mozilla Public License Version 2.0`，使用本项目时请遵循该协议.

另外本项目主要使用了[Proxyee](https://github.com/monkeyWie/proxyee)项目，该项目采用MIT许可证，该许可证副本在本项目位置为`proxyee.LICENSE`