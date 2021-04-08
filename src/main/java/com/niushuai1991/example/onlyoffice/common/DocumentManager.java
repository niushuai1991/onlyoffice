package com.niushuai1991.example.onlyoffice.common;

import com.niushuai1991.example.onlyoffice.entity.FileType;
import org.json.simple.JSONObject;
import org.primeframework.jwt.Signer;
import org.primeframework.jwt.Verifier;
import org.primeframework.jwt.domain.JWT;
import org.primeframework.jwt.hmac.HMACSigner;
import org.primeframework.jwt.hmac.HMACVerifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Component("documentManager")
public class DocumentManager
{
//    private static HttpServletRequest request;
//
//    public void Init(HttpServletRequest req, HttpServletResponse resp)
//    {
//        request = req;
//    }

    @Value("${doc.tempdir}")
    private String tempdir;

    @Value("${storage-folder}")
    private String storageFolder;

    @Value("${doc.serverUrl}")
    private String serverUrl;

    public long GetMaxFileSize()
    {
        long size;

        try
        {
            size = Long.parseLong(ConfigManager.GetProperty("filesize-max"));
        }
        catch (Exception ex)
        {
            size = 0;
        }

        return size > 0 ? size : 5 * 1024 * 1024;
    }

    public List<String> GetFileExts()
    {
        List<String> res = new ArrayList<>();

        res.addAll(GetViewedExts());
        res.addAll(GetEditedExts());
        res.addAll(GetConvertExts());

        return res;
    }

    public List<String> GetViewedExts()
    {
        String exts = ConfigManager.GetProperty("files.docservice.viewed-docs");
        return Arrays.asList(exts.split("\\|"));
    }

    public List<String> GetEditedExts()
    {
        String exts = ConfigManager.GetProperty("files.docservice.edited-docs");
        return Arrays.asList(exts.split("\\|"));
    }

    public List<String> GetConvertExts()
    {
        String exts = ConfigManager.GetProperty("files.docservice.convert-docs");
        return Arrays.asList(exts.split("\\|"));
    }

    public String CurUserHostAddress(String userAddress)
    {
        if(userAddress == null)
        {
            try
            {
                userAddress = InetAddress.getLocalHost().getHostAddress();
            }
            catch (Exception ex)
            {
                userAddress = "";
            }
        }

        return userAddress.replaceAll("[^0-9a-zA-Z.=]", "_");
    }

    public String FilesRootPath(String userAddress)
    {
        String hostAddress = CurUserHostAddress(userAddress);
        String serverPath = tempdir;
        System.out.println("serverPath:"+serverPath);
        String storagePath = ConfigManager.GetProperty("storage-folder");
        String directory = serverPath + storagePath + File.separator + hostAddress + File.separator;

        File file = new File(directory);

        if (!file.exists())
        {
            file.mkdirs();
        }

        return directory;
    }

    public String StoragePath(String fileName, String userAddress)
    {
        String directory = FilesRootPath(userAddress);
        return directory + fileName;
    }

    public String HistoryDir(String storagePath)
    {
        return storagePath += "-hist";
    }

    public String VersionDir(String histPath, Integer version)
    {
        return histPath + File.separator + Integer.toString(version);
    }

    public String VersionDir(String fileName, String userAddress, Integer version)
    {
        return VersionDir(HistoryDir(StoragePath(fileName, userAddress)), version);
    }

    public Integer GetFileVersion(String historyPath)
    {
        File dir = new File(historyPath);

        if (!dir.exists()) return 0;

        File[] dirs = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isDirectory();
            }
        });

        return dirs.length;
    }

    public int GetFileVersion(String fileName, String userAddress)
    {
        return GetFileVersion(HistoryDir(StoragePath(fileName, userAddress)));
    }

    public String GetCorrectName(String fileName)
    {
        String baseName = FileUtility.GetFileNameWithoutExtension(fileName);
        String ext = FileUtility.GetFileExtension(fileName);
        String name = baseName + ext;

        File file = new File(StoragePath(name, null));

        for (int i = 1; file.exists(); i++)
        {
            name = baseName + " (" + i + ")" + ext;
            file = new File(StoragePath(name, null));
        }

        return name;
    }

    public void CreateMeta(String fileName, String uid, String uname) throws Exception
    {
        String histDir = HistoryDir(StoragePath(fileName, null));

        File dir = new File(histDir);
        dir.mkdir();

        JSONObject json = new JSONObject();
        json.put("created", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        json.put("id", (uid == null || uid.isEmpty()) ? "uid-1" : uid);
        json.put("name", (uname == null || uname.isEmpty()) ? "John Smith" : uname);

        File meta = new File(histDir + File.separator + "createdInfo.json");
        try (FileWriter writer = new FileWriter(meta)) {
            json.writeJSONString(writer);
        }
    }

    public File[] GetStoredFiles(String userAddress)
    {
        String directory = FilesRootPath(userAddress);

        File file = new File(directory);
        return file.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.isFile();
            }
        });
    }

    public String CreateDemo(String fileExt, Boolean sample, String uid, String uname) throws Exception
    {
        String demoName = (sample ? "sample." : "new.") + fileExt;
        String fileName = GetCorrectName(demoName);

        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(demoName);

        File file = new File(StoragePath(fileName, null));

        try (FileOutputStream out = new FileOutputStream(file))
        {
            int read;
            final byte[] bytes = new byte[1024];
            while ((read = stream.read(bytes)) != -1)
            {
                out.write(bytes, 0, read);
            }
            out.flush();
        }

        CreateMeta(fileName, uid, uname);

        return fileName;
    }

    /**
     *
     * @return
     */
    public String getDownloadUrl(String fileName){
        try {
            String serverPath = GetServerUrl();
            return serverPath +"/file/" + URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8.toString()).replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return "";
        }
    }

    public String GetFileUri(String fileName)
    {
        try
        {
            String serverPath = GetServerUrl();
            // 这里弃用，因为必须要把app_data目录放到webapp里，不方便服务器维护。
//            String storagePath = ConfigManager.GetProperty("storage-folder");
//            String hostAddress = CurUserHostAddress(null);
//            String filePath = serverPath + "/" + storagePath + "/" + hostAddress + "/" + URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8.toString()).replace("+", "%20");
//            return filePath;

            return serverPath +"/file/" + URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8.toString()).replace("+", "%20");
        }
        catch (UnsupportedEncodingException e)
        {
            return "";
        }
    }

    public String GetPathUri(String path)
    {
        String serverPath = GetServerUrl();
        String storagePath = ConfigManager.GetProperty("storage-folder");
        String hostAddress = CurUserHostAddress(null);

        String filePath = serverPath + "/" + storagePath + "/" + hostAddress + "/" + path.replace(File.separator, "/").substring(FilesRootPath(null).length()).replace(" ", "%20");
//        String filePath = "/" + storagePath + "/" + hostAddress + "/" + path.replace(File.separator, "/").substring(FilesRootPath(null).length()).replace(" ", "%20");

        return filePath;
    }


//    public String GetServerUrl(HttpServletRequest request)
//    {
//        return request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
//    }

    public String GetServerUrl()
    {
        return serverUrl;
    }

    public String GetCallback(String fileName)
    {
        String serverPath = GetServerUrl();
        String hostAddress = CurUserHostAddress(null);
        try
        {
            String query = "?fileName=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()) + "&userAddress=" + URLEncoder.encode(hostAddress, StandardCharsets.UTF_8.toString());
            return serverPath + "/IndexServlet/track" + query;
        }
        catch (UnsupportedEncodingException e)
        {
            return "";
        }
    }

    public String GetInternalExtension(FileType fileType)
    {
        if (fileType.equals(FileType.Text))
            return ".docx";

        if (fileType.equals(FileType.Spreadsheet))
            return ".xlsx";

        if (fileType.equals(FileType.Presentation))
            return ".pptx";

        return ".docx";
    }

    public String CreateToken(Map<String, Object> payloadClaims)
    {
        try
        {
            Signer signer = HMACSigner.newSHA256Signer(GetTokenSecret());
            JWT jwt = new JWT();
            for (String key : payloadClaims.keySet())
            {
                jwt.addClaim(key, payloadClaims.get(key));
            }
            return JWT.getEncoder().encode(jwt, signer);
        }
        catch (Exception e)
        {
            return "";
        }
    }

    public JWT ReadToken(String token)
    {
        try
        {
            Verifier verifier = HMACVerifier.newVerifier(GetTokenSecret());
            return JWT.getDecoder().decode(token, verifier);
        }
        catch (Exception exception)
        {
            return null;
        }
    }

    public Boolean TokenEnabled()
    {
        String secret = GetTokenSecret();
        return secret != null && !secret.isEmpty();
    }

    private static String GetTokenSecret()
    {
        return ConfigManager.GetProperty("files.docservice.secret");
    }
}