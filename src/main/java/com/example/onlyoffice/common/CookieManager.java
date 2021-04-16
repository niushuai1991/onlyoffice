package com.example.onlyoffice.common;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;

public class CookieManager {
    private HashMap<String, String> cookiesMap;

    public CookieManager(HttpServletRequest request) throws UnsupportedEncodingException {
        cookiesMap = new HashMap<String, String>();

        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                cookiesMap.putIfAbsent(cookie.getName(), URLDecoder.decode(cookie.getValue(), "UTF-8"));
            }
        }
    }

    public String getCookie(String name) {
        return cookiesMap.get(name);
    }
}