package com.niushuai1991.example.onlyoffice.entity;

import com.google.common.base.Throwables;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.niushuai1991.example.onlyoffice.common.DocumentManager;
import com.niushuai1991.example.onlyoffice.common.FileUtility;
import com.niushuai1991.example.onlyoffice.common.ServiceConverter;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;

public class FileModel
{
    private Logger logger = LoggerFactory.getLogger(this.getClass());
    public String type = "desktop";
    public String mode = "edit";
    public String documentType;
    public Document document;
    public EditorConfig editorConfig;
    public String token;

    public FileModel(DocumentManager documentManager, String fileName, String lang, String uid, String uname, String actionData)
    {
        if (fileName == null) fileName = "";
        fileName = fileName.trim();

        documentType = FileUtility.GetFileType(fileName).toString().toLowerCase();

        document = new Document();
        document.title = fileName;
        document.url = documentManager.GetFileUri(fileName);
        document.fileType = FileUtility.GetFileExtension(fileName).replace(".", "");
        document.key = ServiceConverter.GenerateRevisionId(documentManager.CurUserHostAddress(null) + "/" + fileName + "/" + Long.toString(new File(documentManager.StoragePath(fileName, null)).lastModified()));

        editorConfig = new EditorConfig(actionData);
        editorConfig.callbackUrl = documentManager.GetCallback(fileName);
        if (lang != null) editorConfig.lang = lang;

        if (uid != null) editorConfig.user.id = uid;
        if (uname != null) editorConfig.user.name = uname;

        editorConfig.customization.goback.url = documentManager.GetServerUrl() + "/IndexServlet";

        changeType(documentManager, mode, type);
    }

    public void changeType(DocumentManager documentManager, String _mode, String _type)
    {
        if (_mode != null) mode = _mode;
        if (_type != null) type = _type;

        Boolean canEdit = documentManager.GetEditedExts().contains(FileUtility.GetFileExtension(document.title));

        editorConfig.mode = canEdit && !mode.equals("view") ? "edit" : "view";

        document.permissions = new Permissions(mode, type, canEdit);

        if (type.equals("embedded")) InitDesktop();
    }

    public void InitDesktop()
    {
        editorConfig.InitDesktop(document.url);
    }

    public void BuildToken(DocumentManager documentManager)
    {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        map.put("documentType", documentType);
        map.put("document", document);
        map.put("editorConfig", editorConfig);

        token = documentManager.CreateToken(map);
    }

    public String[] GetHistory(DocumentManager documentManager)
    {
        JSONParser parser = new JSONParser();
        String histDir = documentManager.HistoryDir(documentManager.StoragePath(document.title, null));
        if (documentManager.GetFileVersion(histDir) > 0) {
            Integer curVer = documentManager.GetFileVersion(histDir);

            Set<Map<String, Object>> hist = new HashSet<>();
            Map<String, Object> histData = new HashMap<>();

            for (Integer i = 0; i <= curVer; i++) {
                Map<String, Object> obj = new HashMap<>();
                Map<String, Object> dataObj = new HashMap<>();
                String verDir = documentManager.VersionDir(histDir, i + 1);

                try {
                    String key = null;

                    key = i == curVer ? document.key : readFileToEnd(new File(verDir + File.separator + "key.txt"));

                    obj.put("key", key);
                    obj.put("version", i);

                    if (i == 0) {
                        String createdInfo = readFileToEnd(new File(histDir + File.separator + "createdInfo.json"));
                        JSONObject json = (JSONObject) parser.parse(createdInfo);

                        obj.put("created", json.get("created"));
                        Map<String, Object> user = new HashMap<String, Object>();
                        user.put("id", json.get("id"));
                        user.put("name", json.get("name"));
                        obj.put("user", user);
                    }

                    dataObj.put("key", key);
                    dataObj.put("url", i == curVer ? document.url : documentManager.GetPathUri(verDir + File.separator + "prev" + FileUtility.GetFileExtension(document.title)));
//                    dataObj.put("url", documentManager.GetServerUrl() +"/file/history?fileName=" + document.title + "&version=" + i);
                    dataObj.put("version", i);

                    if (i > 0) {
                        JSONObject changes = (JSONObject) parser.parse(readFileToEnd(new File(documentManager.VersionDir(histDir, i) + File.separator + "changes.json")));
                        JSONObject change = (JSONObject) ((JSONArray) changes.get("changes")).get(0);

                        obj.put("changes", changes.get("changes"));
                        obj.put("serverVersion", changes.get("serverVersion"));
                        obj.put("created", change.get("created"));
                        obj.put("user", change.get("user"));

                        Map<String, Object> prev = (Map<String, Object>) histData.get(Integer.toString(i - 1));
                        Map<String, Object> prevInfo = new HashMap<String, Object>();
                        prevInfo.put("key", prev.get("key"));
                        prevInfo.put("url", prev.get("url"));
                        dataObj.put("previous", prevInfo);
//                        dataObj.put("changesUrl", documentManager.GetPathUri(documentManager.VersionDir(histDir, i) + File.separator + "diff.zip"));
                        dataObj.put("changesUrl", documentManager.getDiffUrl(document.title, i));
                    }

                    hist.add(obj);
                    histData.put(Integer.toString(i), dataObj);

                } catch (Exception ex) {
                    logger.error(Throwables.getStackTraceAsString(ex));
                }
            }

            Map<String, Object> histObj = new HashMap<String, Object>();
            histObj.put("currentVersion", curVer);
            histObj.put("history", hist);

            Gson gson = new Gson();
            return new String[] { gson.toJson(histObj), gson.toJson(histData) };
        }
        return new String[] { "", "" };
    }

    private String readFileToEnd(File file) {
        StringBuilder output = new StringBuilder();
        try {
            try(FileInputStream is = new FileInputStream(file);Scanner scanner = new Scanner(is)){
                scanner.useDelimiter("\\A");
                while (scanner.hasNext()) {
                    output.append(scanner.next());
                }
            }
        } catch (Exception e) {
            logger.error(Throwables.getStackTraceAsString(e));
        }
        return output.toString();
    }

    public class Document
    {
        public String title;
        public String url;
        public String fileType;
        public String key;
        public Permissions permissions;
    }

    public class Permissions
    {
        public Boolean comment;
        public Boolean download;
        public Boolean edit;
        public Boolean fillForms;
        public Boolean modifyFilter;
        public Boolean modifyContentControl;
        public Boolean review;

        public Permissions(String mode, String type, Boolean canEdit)
        {
            comment = !mode.equals("view") && !mode.equals("fillForms") && !mode.equals("embedded") && !mode.equals("blockcontent");
            download = true;
            edit = canEdit && (mode.equals("edit") || mode.equals("filter") || mode.equals("blockcontent"));
            fillForms = !mode.equals("view") && !mode.equals("comment") && !mode.equals("embedded") && !mode.equals("blockcontent");
            modifyFilter = !mode.equals("filter");
            modifyContentControl = !mode.equals("blockcontent");
            review = mode.equals("edit") || mode.equals("review");
        }
    }

    public class EditorConfig
    {
        public HashMap<String, Object> actionLink = null;
        public String mode = "edit";
        public String callbackUrl;
        public String lang = "en";
        public User user;
        public Customization customization;
        public Embedded embedded;

        public EditorConfig(String actionData)
        {
            if (actionData != null) {
                Gson gson = new Gson();
                actionLink = gson.fromJson(actionData, new TypeToken<HashMap<String, Object>>() { }.getType());
            }
            user = new User();
            customization = new Customization();
        }

        public void InitDesktop(String url)
        {
            embedded = new Embedded();
            embedded.saveUrl = url;
            embedded.embedUrl = url;
            embedded.shareUrl = url;
            embedded.toolbarDocked = "top";
        }

        public class User
        {
            public String id = "uid-1";
            public String name = "John Smith";
        }

        public class Customization
        {
            public Goback goback;

            public Customization()
            {
                goback = new Goback();
            }

            public class Goback
            {
                public String url;
            }
        }

        public class Embedded
        {
            public String saveUrl;
            public String embedUrl;
            public String shareUrl;
            public String toolbarDocked;
        }
    }


    public static String Serialize(FileModel model)
    {
        Gson gson = new Gson();
        return gson.toJson(model);
    }
}
