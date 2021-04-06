package com.niushuai1991.example.onlyoffice.controller;

import com.niushuai1991.example.onlyoffice.common.*;
import com.niushuai1991.example.onlyoffice.entity.FileType;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.primeframework.jwt.domain.JWT;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Scanner;

@Controller
public class IndexController {
    private static final String DocumentJwtHeader = ConfigManager.GetProperty("files.docservice.header");

    @RequestMapping("/index")
    @ResponseBody
    public void index(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = request.getParameter("type");

        if (action == null) {
            request.getRequestDispatcher("index.jsp").forward(request, response);
            return;
        }

        DocumentManager.Init(request, response);
        PrintWriter writer = response.getWriter();

        switch (action.toLowerCase()) {
            case "upload":
                Upload(request, response, writer);
                break;
            case "convert":
                Convert(request, response, writer);
                break;
            case "track":
                Track(request, response, writer);
                break;
            case "remove":
                Remove(request, response, writer);
                break;
        }
    }

    private static void Upload(HttpServletRequest request, HttpServletResponse response, PrintWriter writer) {
        response.setContentType("text/plain");

        try {
            Part httpPostedFile = request.getPart("file");

            String fileName = "";
            for (String content : httpPostedFile.getHeader("content-disposition").split(";")) {
                if (content.trim().startsWith("filename")) {
                    fileName = content.substring(content.indexOf('=') + 1).trim().replace("\"", "");
                }
            }

            long curSize = httpPostedFile.getSize();
            if (DocumentManager.GetMaxFileSize() < curSize || curSize <= 0) {
                writer.write("{ \"error\": \"File size is incorrect\"}");
                return;
            }

            String curExt = FileUtility.GetFileExtension(fileName);
            if (!DocumentManager.GetFileExts().contains(curExt)) {
                writer.write("{ \"error\": \"File type is not supported\"}");
                return;
            }

            InputStream fileStream = httpPostedFile.getInputStream();

            fileName = DocumentManager.GetCorrectName(fileName);
            String fileStoragePath = DocumentManager.StoragePath(fileName, null);

            File file = new File(fileStoragePath);

            try (FileOutputStream out = new FileOutputStream(file)) {
                int read;
                final byte[] bytes = new byte[1024];
                while ((read = fileStream.read(bytes)) != -1) {
                    out.write(bytes, 0, read);
                }

                out.flush();
            }

            CookieManager cm = new CookieManager(request);
            DocumentManager.CreateMeta(fileName, cm.getCookie("uid"), cm.getCookie("uname"));

            writer.write("{ \"filename\": \"" + fileName + "\"}");

        } catch (Exception e) {
            writer.write("{ \"error\": \"" + e.getMessage() + "\"}");
        }
    }

    private static void Convert(HttpServletRequest request, HttpServletResponse response, PrintWriter writer) {
        response.setContentType("text/plain");

        try {
            String fileName = request.getParameter("filename");
            String fileUri = DocumentManager.GetFileUri(fileName);
            String fileExt = FileUtility.GetFileExtension(fileName);
            FileType fileType = FileUtility.GetFileType(fileName);
            String internalFileExt = DocumentManager.GetInternalExtension(fileType);

            if (DocumentManager.GetConvertExts().contains(fileExt)) {
                String key = ServiceConverter.GenerateRevisionId(fileUri);

                String newFileUri = ServiceConverter.GetConvertedUri(fileUri, fileExt, internalFileExt, key, true);

                if (newFileUri.isEmpty()) {
                    writer.write("{ \"step\" : \"0\", \"filename\" : \"" + fileName + "\"}");
                    return;
                }

                String correctName = DocumentManager.GetCorrectName(FileUtility.GetFileNameWithoutExtension(fileName) + internalFileExt);

                URL url = new URL(newFileUri);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) url.openConnection();
                InputStream stream = connection.getInputStream();

                if (stream == null) {
                    throw new Exception("Stream is null");
                }

                File convertedFile = new File(DocumentManager.StoragePath(correctName, null));
                try (FileOutputStream out = new FileOutputStream(convertedFile)) {
                    int read;
                    final byte[] bytes = new byte[1024];
                    while ((read = stream.read(bytes)) != -1) {
                        out.write(bytes, 0, read);
                    }

                    out.flush();
                }

                connection.disconnect();

                //remove source file ?
                //File sourceFile = new File(DocumentManager.StoragePath(fileName, null));
                //sourceFile.delete();

                fileName = correctName;

                CookieManager cm = new CookieManager(request);
                DocumentManager.CreateMeta(fileName, cm.getCookie("uid"), cm.getCookie("uname"));
            }

            writer.write("{ \"filename\" : \"" + fileName + "\"}");

        } catch (Exception ex) {
            writer.write("{ \"error\": \"" + ex.getMessage() + "\"}");
        }
    }

    private static void Track(HttpServletRequest request, HttpServletResponse response, PrintWriter writer) {
        String userAddress = request.getParameter("userAddress");
        String fileName = request.getParameter("fileName");

        String storagePath = DocumentManager.StoragePath(fileName, userAddress);
        String body = "";

        try {
            Scanner scanner = new Scanner(request.getInputStream());
            scanner.useDelimiter("\\A");
            body = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
        } catch (Exception ex) {
            writer.write("get request.getInputStream error:" + ex.getMessage());
            return;
        }

        if (body.isEmpty()) {
            writer.write("empty request.getInputStream");
            return;
        }

        JSONParser parser = new JSONParser();
        JSONObject jsonObj;

        try {
            Object obj = parser.parse(body);
            jsonObj = (JSONObject) obj;
        } catch (Exception ex) {
            writer.write("JSONParser.parse error:" + ex.getMessage());
            return;
        }

        int status;
        String downloadUri;
        String changesUri;
        String key;

        if (DocumentManager.TokenEnabled()) {
            String token = (String) jsonObj.get("token");

            if (token == null) {
                String header = (String) request.getHeader(DocumentJwtHeader == null || DocumentJwtHeader.isEmpty() ? "Authorization" : DocumentJwtHeader);
                if (header != null && !header.isEmpty()) {
                    token = header.startsWith("Bearer ") ? header.substring(7) : header;
                }
            }

            if (token == null || token.isEmpty()) {
                writer.write("{\"error\":1,\"message\":\"JWT expected\"}");
                return;
            }

            JWT jwt = DocumentManager.ReadToken(token);
            if (jwt == null) {
                writer.write("{\"error\":1,\"message\":\"JWT validation failed\"}");
                return;
            }

            if (jwt.getObject("payload") != null) {
                try {
                    @SuppressWarnings("unchecked") LinkedHashMap<String, Object> payload =
                            (LinkedHashMap<String, Object>) jwt.getObject("payload");

                    jwt.claims = payload;
                } catch (Exception ex) {
                    writer.write("{\"error\":1,\"message\":\"Wrong payload\"}");
                    return;
                }
            }

            status = jwt.getInteger("status");
            downloadUri = jwt.getString("url");
            changesUri = jwt.getString("changesurl");
            key = jwt.getString("key");
        } else {
            status = Math.toIntExact((long) jsonObj.get("status"));
            downloadUri = (String) jsonObj.get("url");
            changesUri = (String) jsonObj.get("changesurl");
            key = (String) jsonObj.get("key");
        }

        int saved = 0;
        if (status == 2 || status == 3)//MustSave, Corrupted
        {
            try {
                String histDir = DocumentManager.HistoryDir(storagePath);
                String versionDir = DocumentManager.VersionDir(histDir, DocumentManager.GetFileVersion(histDir) + 1);
                File ver = new File(versionDir);
                File toSave = new File(storagePath);

                if (!ver.exists()) ver.mkdirs();

                toSave.renameTo(new File(versionDir + File.separator + "prev" + FileUtility.GetFileExtension(fileName)));

                downloadToFile(downloadUri, toSave);
                downloadToFile(changesUri, new File(versionDir + File.separator + "diff.zip"));

                String history = (String) jsonObj.get("changeshistory");
                if (history == null && jsonObj.containsKey("history")) {
                    history = ((JSONObject) jsonObj.get("history")).toJSONString();
                }
                if (history != null && !history.isEmpty()) {
                    FileWriter fw = new FileWriter(new File(versionDir + File.separator + "changes.json"));
                    fw.write(history);
                    fw.close();
                }

                FileWriter fw = new FileWriter(new File(versionDir + File.separator + "key.txt"));
                fw.write(key);
                fw.close();
            } catch (Exception ex) {
                saved = 1;
            }
        }

        writer.write("{\"error\":" + saved + "}");
    }

    private static void Remove(HttpServletRequest request, HttpServletResponse response, PrintWriter writer) {
        try {
            String fileName = request.getParameter("filename");
            String path = DocumentManager.StoragePath(fileName, null);

            File f = new File(path);
            delete(f);

            File hist = new File(DocumentManager.HistoryDir(path));
            delete(hist);

            writer.write("{ \"success\": true }");
        } catch (Exception e) {
            writer.write("{ \"error\": \"" + e.getMessage() + "\"}");
        }
    }

    private static void delete(File f) throws Exception {
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        if (!f.delete())
            throw new Exception("Failed to delete file: " + f);
    }

    private static void downloadToFile(String url, File file) throws Exception {
        if (url == null || url.isEmpty()) throw new Exception("argument url");
        if (file == null) throw new Exception("argument path");

        URL uri = new URL(url);
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection) uri.openConnection();
        InputStream stream = connection.getInputStream();

        if (stream == null) {
            throw new Exception("Stream is null");
        }

        try (FileOutputStream out = new FileOutputStream(file)) {
            int read;
            final byte[] bytes = new byte[1024];
            while ((read = stream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }

            out.flush();
        }

        connection.disconnect();
    }
}
