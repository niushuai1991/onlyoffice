/*
 *
 * (c) Copyright Ascensio System SIA 2020
 *
 * The MIT License (MIT)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
*/


package com.example.onlyoffice.common;

import com.example.onlyoffice.entity.FileType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FileUtility
{
    static {}

    public static FileType GetFileType(String fileName)
    {
        String ext = GetFileExtension(fileName).toLowerCase();

        if (ExtsDocument.contains(ext))
            return FileType.Text;

        if (ExtsSpreadsheet.contains(ext))
            return FileType.Spreadsheet;

        if (ExtsPresentation.contains(ext))
            return FileType.Presentation;

        return FileType.Text;
    }

    public static List<String> ExtsDocument = Arrays.asList
            (
                    ".doc", ".docx", ".docm",
                    ".dot", ".dotx", ".dotm",
                    ".odt", ".fodt", ".ott", ".rtf", ".txt",
                    ".html", ".htm", ".mht",
                    ".pdf", ".djvu", ".fb2", ".epub", ".xps"
            );

    public static List<String> ExtsSpreadsheet = Arrays.asList
            (
                    ".xls", ".xlsx", ".xlsm",
                    ".xlt", ".xltx", ".xltm",
                    ".ods", ".fods", ".ots", ".csv"
            );

    public static List<String> ExtsPresentation = Arrays.asList
            (
                    ".pps", ".ppsx", ".ppsm",
                    ".ppt", ".pptx", ".pptm",
                    ".pot", ".potx", ".potm",
                    ".odp", ".fodp", ".otp"
            );


    public static String GetFileName(String url)
    {
        if (url == null) return null;

        //for external file url
        String tempstorage = ConfigManager.GetProperty("files.docservice.url.tempstorage");
        if (!tempstorage.isEmpty() && url.startsWith(tempstorage))
        {
            Map<String, String> params = GetUrlParams(url);
            return params == null ? null : params.get("filename");
        }

        String fileName = url.substring(url.lastIndexOf('/') + 1, url.length());
        return fileName;
    }

    public static String GetFileNameWithoutExtension(String url)
    {
        String fileName = GetFileName(url);
        if (fileName == null) return null;
        String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
        return fileNameWithoutExt;
    }

    public static String GetFileExtension(String url)
    {
        String fileName = GetFileName(url);
        if (fileName == null) return null;
        String fileExt = fileName.substring(fileName.lastIndexOf("."));
        return fileExt.toLowerCase();
    }

    public static Map<String, String> GetUrlParams(String url)
    {
        try
        {
            String query = new URL(url).getQuery();
            String[] params = query.split("&");
            Map<String, String> map = new HashMap<>();
            for (String param : params)
            {
                String name = param.split("=")[0];
                String value = param.split("=")[1];
                map.put(name, value);
            }
            return map;
        }
        catch (Exception ex)
        {
            return null;
        }
    }

    /**
     * 文件转字节数组
     *
     * @param path
     * @return
     * @throws IOException
     */
    public static byte[] fileToByteArray(String path) throws IOException {
        File file = new File(path);
        byte[] fileBytes;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream(); InputStream fileStream = new FileInputStream(file)) {
            int read;
            final byte[] bytes = new byte[1024];
            while ((read = fileStream.read(bytes)) != -1) {
                out.write(bytes, 0, read);
            }
            out.flush();
            fileBytes = out.toByteArray();
        }
        return fileBytes;
    }

    public static ResponseEntity<byte[]> fileToResponseEntity(String fileName, String path) throws IOException {
        byte[] bytes = fileToByteArray(path);
        HttpHeaders headers = new HttpHeaders();
        headers.set("Access-Control-Allow-Origin", "*");
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", new String(fileName.getBytes("utf-8"),"iso-8859-1"));
        ResponseEntity<byte[]> responseEntity = new ResponseEntity<>(bytes, headers, HttpStatus.OK);
        return responseEntity;
    }

    public static void writeFile(String tempFile, InputStream in, String charset) throws IOException {
        File file = new File(tempFile);
        String sTempOneLine;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, "utf-8"));BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file), charset))) {
            while ((sTempOneLine = reader.readLine()) != null){
                writer.append(sTempOneLine).append("\n");
            }
            writer.flush();
        }
    }
}
