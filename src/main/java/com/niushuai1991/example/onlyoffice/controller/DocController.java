package com.niushuai1991.example.onlyoffice.controller;


import com.google.common.base.Throwables;
import com.niushuai1991.example.onlyoffice.common.ConfigManager;
import com.niushuai1991.example.onlyoffice.common.CookieManager;
import com.niushuai1991.example.onlyoffice.common.DocumentManager;
import com.niushuai1991.example.onlyoffice.common.FileUtility;
import com.niushuai1991.example.onlyoffice.entity.FileModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Controller
public class DocController {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    @Resource
    private DocumentManager documentManager;

    @RequestMapping("/EditorServlet")
    public ModelAndView editor(HttpServletRequest request) throws IOException, ServletException {
        Cookie[] cookies = request.getCookies();
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


}
