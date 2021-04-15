package com.niushuai1991.example.onlyoffice.controller;


import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.niushuai1991.example.onlyoffice.common.ConfigManager;
import com.niushuai1991.example.onlyoffice.common.CookieManager;
import com.niushuai1991.example.onlyoffice.common.DocumentManager;
import com.niushuai1991.example.onlyoffice.common.FileUtility;
import com.niushuai1991.example.onlyoffice.entity.FileModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
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

    @RequestMapping("/EditorServlet")
    public ModelAndView editor(HttpServletRequest request) throws IOException, ServletException {
        String fileName = request.getParameter("fileName");
        String fileExt = request.getParameter("fileExt");
        String sample = request.getParameter("sample");
        Boolean sampleData = (sample == null || sample.isEmpty()) ? false : sample.toLowerCase().equals("true");
        CookieManager cm = new CookieManager(request);
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

        FileModel file = new FileModel(documentManager, fileName, cm.getCookie("ulang"), cm.getCookie("uid"), cm.getCookie("uname"), request.getParameter("actionLink"));
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
     * 编辑文档
     * 当编辑的文档不存在时，通过传入的模板文档名来创建
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
        String ulang = cm.getCookie("ulang");

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
     * 下载文件
     * @return
     */
    @RequestMapping("/file/{fileName}")
    @ResponseBody
    public ResponseEntity<byte[]> getfile(@PathVariable String fileName) throws IOException {
        String fileStoragePath = documentManager.StoragePath(fileName, null);
        return FileUtility.fileToResponseEntity(fileName, fileStoragePath);
    }

    /**
     * 获取文件差异信息
     * @param fileName
     * @param version
     * @return
     * @throws IOException
     */
    @RequestMapping("/fileDiff")
    @ResponseBody
    public ResponseEntity<byte[]> history(String fileName, Integer version) throws IOException {
        String diffPath = documentManager.getDiffPath(fileName, version);
        logger.info("下载文件差异信息:{}", diffPath);
        return FileUtility.fileToResponseEntity("diff.zip", diffPath);
    }


    /**
     * 根据模板创建
     * @param fileName
     * @param template
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    @RequestMapping("/createByTemplate")
    public ModelAndView createByTemplate(String fileName, String template) throws IOException, InterruptedException {
        if (Strings.isNullOrEmpty(template)) {
            return new ModelAndView("message").addObject("message", "模板不能为空!");
        }
        if (Strings.isNullOrEmpty(fileName)) {
            return new ModelAndView("message").addObject("message", "文件不能为空!");
        }
        String templatePath = documentManager.StoragePath(template, null);
        File templateFile = new File(templatePath);
        if (!templateFile.exists()) {
            return new ModelAndView("message").addObject("message", "模板文件不存在！");
        }
        File file = new File(fileName);
        if (file.exists()) {
            return new ModelAndView("message").addObject("message", "文档已存在！");
        }
        String filePath = documentManager.StoragePath(fileName, null);

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(String.format("builder.OpenFile(\"%s\");\n", templatePath));
        stringBuilder.append("oDocument = Api.GetDocument();\n");
        // TODO 这里要改成传参
        stringBuilder.append("oDocument.SearchAndReplace({\"searchString\": \"${Name}\", \"replaceString\": \"张三\"});\n");
        stringBuilder.append(String.format("builder.SaveFile(\"docx\", \"%s\");\n", filePath));
        stringBuilder.append("builder.CloseFile();\n");
        // 生成docbuilder文件
        String tempFile = System.currentTimeMillis() +".docbuilder";
        String tempFilePath = documentManager.StoragePath(tempFile, null);
        logger.info("生成docbuilder文件:{}", tempFilePath);
        try (InputStream in = new ByteArrayInputStream(stringBuilder.toString().getBytes())) {
            FileUtility.writeFile(tempFilePath, in);
        }
        // 调用docbuilder执行docbuilder文件
        Process process = Runtime.getRuntime().exec(docbuilderPath + " " + tempFilePath);
        try (BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String s;
            while ((s = stdInput.readLine()) != null) {
                logger.info(s);
            }
        }
        try (BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String s;
            while ((s = stdError.readLine()) != null) {
                logger.info(s);
            }
        }
        logger.info("调用docbuilder执行结果：{}", process.waitFor());
        // 删除docbuilder文件
        new File(tempFilePath).deleteOnExit();
        // 跳转到编辑器页面
        return new ModelAndView("redirect:/EditorServlet?fileName=" + URLEncoder.encode(fileName, "UTF-8"));
    }


}
