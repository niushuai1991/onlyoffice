# onlyoffice在线编辑


## 与官方demo的区别

项目是基于官方demo的源码修改而来，主要做了以下改动：

1. 官方demo的app_data目录必须是在项目的webapp目录中，现在可以配置在系统的任何目录，方便管理维护。
2. 改为SpringBoot项目。
3. 将Servlet改为了SpringMVC。
4. 修复了获取文件差异信息时请求跨域的问题。


## 配置

**doc.serverUrl**是配置服务器的地址，比如http://192.168.1.10:8087，不能使用127.0.0.1或者localhost，
因为这个地址需要在documentserver容器中访问。

## 部署

需要依赖onlyoffice的 DocumentServer容器

```
docker run -i -t -d --name documentserver -p 6831:80 onlyoffice/documentserver
```

如果需要开机启动，加上--restart=always参数。

编译项目

```
mvn package -DskipTests
```

因为是基于SpringBoot，所以可以直接执行
```
java -jar onlyoffice-0.0.1-SNAPSHOT.war
```
