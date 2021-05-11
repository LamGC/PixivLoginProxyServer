# PixivLoginProxyServer
A pixiv crawler login program for deceiving reCAPTCHA, applicable to Java related crawlers, but welcome to extend!
## 配置文件 ##
当缺少配置文件时, 将会使用 `default.properties`：
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
第一次运行后将会导出 `plps.properties` 文件，请根据注释修改值。

## 如何使用 ##
### 使用 PixivLoginProxyServer 登录 Pixiv 获得 CookieStore ###
第一次运行时, 由于 Pixiv 使用了 Https 加密了访问，所以请打开 `127.0.0.1:{proxyPort}` 以下载CA证书并进行导入，   
导入时，请将证书导入到`受信任的根证书颁发机构`，导入成功即可。  
第一次运行不存在配置文件，程序将会初始化配置文件并提示修改配置文件，更改好配置文件后启动程序，并设置浏览器代理，  
使用浏览器登录 Pixiv 时推荐使用浏览器隐身模式登录，可以不需要退出浏览器已登录的 Pixiv，更方便的登录 Pixiv。  
登录后输入`close`回车即可，如上次存在导出的 CookieStore，则按情况选择是否覆盖。

#### 安全问题：CA证书 ####
如担心CA证书的导入出现安全问题，可在完成 Pixiv 登录并导出 CookieStore 后从证书库删除CA证书：
##### (1) 导入时选择存储位置为“本地计算机” #####
**以管理员身份运行** `certmgr.msc`，  
左侧依次进入“受信任的根证书颁发机构 -> 证书”，右侧列表找到证书颁发者为“Proxyee”的证书，并将其删除后即可。

##### (2) 导入时选择存储位置为“当前用户” #####
**直接使用运行（Win + R）打开** `certmgr.msc`（即不使用管理员身份运行），  
左侧依次进入“受信任的根证书颁发机构 -> 证书”，右侧列表找到证书颁发者为“Proxyee”的证书，并将其删除后即可。

##### (3) Firefox 浏览器 #####
> 以`Firefox 73`为例  

在 Firefox 浏览器中打开 `about:preferences#advanced`，转到**隐私和安全**，  
找到最下面的`证书`一块，点击**查看证书**，在弹出来的证书管理器中，进入证书颁发机构，  
找到颁发名为 **lee** 的 `Proxyee` 证书，点击`删除或不信任(D)...`，并在确认框中确认删除即可。

### 其他程序导入 PixivLoginProxyServer 导出的 CookieStore ###
PixivLoginProxyServer 所采用的 CookieStore 来自 `org.apache.http.client.CookieStore`，  
导入时仅需要使用 `ObjectInputStream` 读取后转换即可。

## LICENSE ##
本项目采用 `Mozilla Public License Version 2.0`，使用本项目时请遵循该协议.

另外本项目主要使用了 [Proxyee](https://github.com/monkeyWie/proxyee) 项目，该项目采用MIT许可证，该许可证副本在本项目位置为`proxyee.LICENSE`
