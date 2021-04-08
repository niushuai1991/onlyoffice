# onlyoffice在线编辑


## 与官方demo的区别

项目是基于官方demo的源码修改而来，主要做了以下改动：

1. 官方demo的app_data目录必须是在项目的webapp目录中，现在可以配置在系统的任何目录，方便管理维护。
2. 改为SpringBoot项目。
3. 将Servlet改为了SpringMVC。


需要依赖onlyoffice的 DocumentServer容器

```
docker run -i -t -d --name documentserver -p 6831:80 onlyoffice/documentserver
```

如果需要开机启动，加上--restart=always参数。

然后修改settings.properties中以files.docservice.url开头的4个配置，需要把ip改为本机的ip，不能使用127.0.0.1或localhost，
因为在documentserver容器中访问不到。


