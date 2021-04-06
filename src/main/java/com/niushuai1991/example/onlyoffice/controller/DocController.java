package com.niushuai1991.example.onlyoffice.controller;


import com.niushuai1991.example.onlyoffice.common.ConfigManager;
import com.niushuai1991.example.onlyoffice.common.CookieManager;
import com.niushuai1991.example.onlyoffice.common.DocumentManager;
import com.niushuai1991.example.onlyoffice.entity.FileModel;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

@Controller
@RequestMapping("/doc")
public class DocController {


    @RequestMapping("/EditorServlet")
    public void editor(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
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
                fileName = DocumentManager.CreateDemo(fileExt, sampleData, cm.getCookie("uid"), cm.getCookie("uname"));
                response.sendRedirect("EditorServlet?fileName=" + URLEncoder.encode(fileName, "UTF-8"));
                return;
            }
            catch (Exception ex)
            {
                response.getWriter().write("Error: " + ex.getMessage());
            }
        }

        FileModel file = new FileModel(fileName, cm.getCookie("ulang"), cm.getCookie("uid"), cm.getCookie("uname"), request.getParameter("actionLink"));
        file.changeType(request.getParameter("mode"), request.getParameter("type"));

        if (DocumentManager.TokenEnabled())
        {
            file.BuildToken();
        }

        request.setAttribute("file", file);
        request.setAttribute("docserviceApiUrl", ConfigManager.GetProperty("files.docservice.url.api"));
        request.getRequestDispatcher("editor.jsp").forward(request, response);
    }


}
