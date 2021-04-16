<%@page import="com.example.onlyoffice.entity.FileModel"%>
<%@ page import="com.example.onlyoffice.common.DocumentManager" %>
<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="org.springframework.context.ApplicationContext" %>
<%@page contentType="text/html" pageEncoding="UTF-8"%>

<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=no, minimal-ui" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="mobile-web-app-capable" content="yes" />
        <title>ONLYOFFICE</title>
        <link rel="icon" href="favicon.ico" type="image/x-icon" />
        <link rel="stylesheet" type="text/css" href="css/editor.css" />

        <% FileModel Model = (FileModel) request.getAttribute("file"); %>

        <script type="text/javascript" src="${docserviceApiUrl}"></script>

        <script type="text/javascript" language="javascript">

        var docEditor;

        var innerAlert = function (message) {
            if (console && console.log)
                console.log(message);
        };

        var onAppReady = function () {
            innerAlert("Document editor ready");
        };

        var onDocumentStateChange = function (event) {
            var title = document.title.replace(/\*$/g, "");
            document.title = title + (event.data ? "*" : "");
        };

        var onRequestEditRights = function () {
            location.href = location.href.replace(RegExp("mode=view\&?", "i"), "");
        };

        var onError = function (event) {
            if (event)
                innerAlert(event.data);
        };

        var onOutdatedVersion = function (event) {
            location.reload(true);
        };

        var replaceActionLink = function(href, linkParam) {
            var link;
            var actionIndex = href.indexOf("&actionLink=");
            if (actionIndex != -1) {
                var endIndex = href.indexOf("&", actionIndex + "&actionLink=".length);
                if (endIndex != -1) {
                    link = href.substring(0, actionIndex) + href.substring(endIndex) + "&actionLink=" + encodeURIComponent(linkParam);
                } else {
                    link = href.substring(0, actionIndex) + "&actionLink=" + encodeURIComponent(linkParam);
                }
            } else {
                link = href + "&actionLink=" + encodeURIComponent(linkParam);
            }
            return link;
        }

        var onMakeActionLink = function (event) {
            var actionData = event.data;
            var linkParam = JSON.stringify(actionData);
            docEditor.setActionLink(replaceActionLink(location.href, linkParam));
        };

        var config = JSON.parse('<%= FileModel.Serialize(Model) %>');
        config.width = "100%";
        config.height = "100%";
        config.events = {
            "onAppReady": onAppReady,
            "onDocumentStateChange": onDocumentStateChange,
            'onRequestEditRights': onRequestEditRights,
            "onError": onError,
            "onOutdatedVersion": onOutdatedVersion,
            "onMakeActionLink": onMakeActionLink,
        };

        <%
                ServletContext context = request.getSession().getServletContext();
                ApplicationContext ctx = WebApplicationContextUtils.getWebApplicationContext(context);
                DocumentManager documentManager = (DocumentManager)ctx.getBean("documentManager");
                %>
        <%

            String[] histArray = Model.GetHistory(documentManager);
            String history = histArray[0];
            String historyData = histArray[1];
        %>

        <% if (!history.isEmpty() && !historyData.isEmpty()) { %>
            config.events['onRequestHistory'] = function () {
                docEditor.refreshHistory(<%= history %>);
            };
            config.events['onRequestHistoryData'] = function (event) {
                var ver = event.data;
                var histData = <%= historyData %>;
                docEditor.setHistoryData(histData[ver]);
            };
            config.events['onRequestHistoryClose'] = function () {
                document.location.reload();
            };
        <% } %>

        var сonnectEditor = function () {
            docEditor = new DocsAPI.DocEditor("iframeEditor", config);
        };

        if (window.addEventListener) {
            window.addEventListener("load", сonnectEditor);
        } else if (window.attachEvent) {
            window.attachEvent("load", сonnectEditor);
        }

    </script>

    </head>
    <body>
        <div class="form">
            <div id="iframeEditor"></div>
        </div>
    </body>
</html>
