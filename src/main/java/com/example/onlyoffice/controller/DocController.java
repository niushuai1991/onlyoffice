package com.example.onlyoffice.controller;


import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.example.onlyoffice.common.ConfigManager;
import com.example.onlyoffice.common.CookieManager;
import com.example.onlyoffice.common.DocumentManager;
import com.example.onlyoffice.common.FileUtility;
import com.example.onlyoffice.entity.FileModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.util.Random;

@Controller
public class DocController {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    @Resource
    private DocumentManager documentManager;
    @Value("${docbuilder.path}")
    private String docbuilderPath;
    @Value("${doc.lang}")
    private String lang;

    @RequestMapping("/EditorServlet")
    public ModelAndView editor(HttpServletRequest request) throws IOException, ServletException {
        String fileName = request.getParameter("fileName");
        String fileExt = request.getParameter("fileExt");
        String sample = request.getParameter("sample");
        Boolean sampleData = (sample == null || sample.isEmpty()) ? false : sample.toLowerCase().equals("true");
        CookieManager cm = new CookieManager(request);
        String ulang = Strings.isNullOrEmpty(lang) ? cm.getCookie("ulang") : lang;
        if (fileExt != null)
        {
            try
            {
                fileName = documentManager.CreateDemo(fileExt, sampleData, cm.getCookie("uid"), cm.getCookie("uname"));
                return new ModelAndView("redirect:/EditorServlet?fileName=" + URLEncoder.encode(fileName, "UTF-8"));
            }
            catch (Exception ex)
            {
                logger.error(Throwables.getStackTraceAsString(ex));
                return new ModelAndView("message").addObject("message", ex.getMessage());
            }
        }

        FileModel file = new FileModel(documentManager, fileName, ulang, cm.getCookie("uid"), cm.getCookie("uname"), request.getParameter("actionLink"));
        file.changeType(documentManager, request.getParameter("mode"), request.getParameter("type"));

        if (documentManager.TokenEnabled())
        {
            file.BuildToken(documentManager);
        }

        ModelAndView mv = new ModelAndView("editor").addObject("docserviceApiUrl", ConfigManager.GetProperty("files.docservice.url.api"));
        mv.addObject("file", file);
        return mv;
    }

    /**
     * ????????????
     * ????????????????????????????????????????????????????????????????????????
     * @param fileName
     * @param template
     * @return
     * @throws IOException
     * @throws ServletException
     */
    @RequestMapping("/editor")
    public ModelAndView editor(HttpServletRequest request, String fileName, String template, String actionLink, String mode, String type) throws IOException {
        CookieManager cm = new CookieManager(request);
        String uid = cm.getCookie("uid");
        String uname = cm.getCookie("uname");
        String ulang = Strings.isNullOrEmpty(lang) ? cm.getCookie("ulang") : lang;

        documentManager.createByTemplateIfNotTxist(fileName, template, uid, uname);

        FileModel file = new FileModel(documentManager, fileName, ulang, uid, uname, actionLink);
        file.changeType(documentManager, mode, type);
        if (documentManager.TokenEnabled()) {
            file.BuildToken(documentManager);
        }
        ModelAndView mv = new ModelAndView("editor");
        mv.addObject("docserviceApiUrl", ConfigManager.GetProperty("files.docservice.url.api"));
        mv.addObject("file", file);
        return mv;
    }


    /**
     * ????????????
     * @return
     */
    @RequestMapping("/file/{fileName}")
    @ResponseBody
    public ResponseEntity<byte[]> getfile(@PathVariable String fileName) throws IOException {
        String fileStoragePath = documentManager.StoragePath(fileName, null);
        return FileUtility.fileToResponseEntity(fileName, fileStoragePath);
    }

    /**
     * ????????????????????????
     * @param fileName
     * @param version
     * @return
     * @throws IOException
     */
    @RequestMapping("/fileDiff")
    @ResponseBody
    public ResponseEntity<byte[]> history(String fileName, Integer version) throws IOException {
        String diffPath = documentManager.getDiffPath(fileName, version);
        logger.info("????????????????????????:{}", diffPath);
        return FileUtility.fileToResponseEntity("diff.zip", diffPath);
    }


    /**
     * ??????????????????
     *
     * @param fileName  ????????? ?????????123.docx
     * @param template  ??????????????? ????????? sample.docx
     * @param data json??????????????????[{"name":"name","value":"??????"},{"name":"??????","value":"20"}]
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    @PostMapping("/createByTemplate")
    @ResponseBody
    @CrossOrigin
    public String createByTemplate(String fileName, String template, String data) throws IOException, InterruptedException {
        String message="";
        if (Strings.isNullOrEmpty(template)) {
            return message="??????????????????!";
        }
        if (Strings.isNullOrEmpty(fileName)) {
            return message="??????????????????!";
        }
        String templatePath = documentManager.StoragePath(template, null);
        File templateFile = new File(templatePath);
        String fileNamePath = documentManager.StoragePath(fileName, null);
        if (!templateFile.exists()) {
            return message="?????????????????????!";
        }
        File file = new File(fileNamePath);
        if (file.exists()) {
            String url=fileName;
            return url;
        }
        JSONArray jsonArray = new JSONArray();
        if (!Strings.isNullOrEmpty(data)) {
            try {
                jsonArray = new JSONArray(data);
            } catch (JSONException e) {
                logger.error("data?????????????????????json??????");
                return message="data?????????????????????json??????!";
            }
        }
        String filePath = documentManager.StoragePath(fileName, null);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("builder.OpenFile(\"%s\");\n", templatePath));
        stringBuilder.append("oDocument = Api.GetDocument();\n");
        // ???????????????????????????????????????
        String replaceTemplate = "oDocument.SearchAndReplace({\"searchString\": \"${%s}\", \"replaceString\": \"%s\"});\n";
        for (int i=0 ;i< jsonArray.length();i++) {
            JSONObject json = jsonArray.getJSONObject(i);
            stringBuilder.append(String.format(replaceTemplate, json.get("name"), json.get("value")));
        }
        stringBuilder.append(String.format("builder.SaveFile(\"docx\", \"%s\");\n", filePath));
        stringBuilder.append("builder.CloseFile();\n");
        // ??????docbuilder??????
        String tempFile = System.currentTimeMillis() +".docbuilder";
        String tempFilePath = documentManager.StoragePath(tempFile, null);
        logger.info("??????docbuilder??????:{}", tempFilePath);
        try (InputStream in = new ByteArrayInputStream(stringBuilder.toString().getBytes())) {
            FileUtility.writeFile(tempFilePath, in, "UTF-8");
        }
        // ??????docbuilder??????docbuilder??????
        Process process = Runtime.getRuntime().exec(docbuilderPath + " " + tempFilePath);
        logger.info("??????docbuilder???????????????{}", process.waitFor());
        try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String s;
            while ((s = stdInput.readLine()) != null) {
                logger.info(s);
            }
            while ((s = stdError.readLine()) != null) {
                logger.info(s);
            }
        }
        if (process != null) {
            process.destroy();
        }
        // ??????docbuilder??????
        new File(tempFilePath).delete();
        // ????????????????????????
        String url=fileName;
        return url;
    }



}
