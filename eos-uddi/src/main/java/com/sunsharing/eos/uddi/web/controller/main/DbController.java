package com.sunsharing.eos.uddi.web.controller.main;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sunsharing.component.resvalidate.util.Encoding;
import com.sunsharing.component.resvalidate.util.SinoDetect;
import com.sunsharing.component.utils.base.DateUtils;
import com.sunsharing.component.utils.base.StringUtils;
import com.sunsharing.eos.uddi.model.*;
import com.sunsharing.eos.uddi.service.AppService;
import com.sunsharing.eos.uddi.service.DbChangeService;
import com.sunsharing.eos.uddi.sys.SysInit;
import com.sunsharing.eos.uddi.web.common.ResponseHelper;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.*;

/**
 * Created by criss on 16/2/25.
 */
@Controller
public class DbController {

    Logger logger = Logger.getLogger(DbController.class);

    @Autowired
    DbChangeService dbChangeService;

    @RequestMapping(value="/listDb.do",method= RequestMethod.POST)
    public void eos(Model model,HttpServletRequest request,
                    HttpServletResponse response) throws Exception {

        Map<Integer,Boolean> changeStatus = new HashMap<Integer, Boolean>();
        Map<Integer,String> userName = new HashMap<Integer, String>();

        String appId = request.getParameter("appId");
        String status = request.getParameter("status");
        List<TDbChange> changeList = dbChangeService.list(appId);

        for(TDbChange change:changeList)
        {
            userName.put(change.getId(),change.getUser().getUserName());
            changeStatus.put(change.getId(),change.geCheckStatus());
        }
        String arrayToStr = JSONArray.toJSONString(changeList);
        JSONArray arrayTo = JSONArray.parseArray(arrayToStr);
        for (Iterator iterator = arrayTo.iterator(); iterator.hasNext(); ) {
            JSONObject next =  (JSONObject)iterator.next();
            String pubishTime = (String)next.get("pubishTime");
            int id = next.getInteger("id");
            boolean checkStatus = changeStatus.get(id);
            if(status.equals("1"))
            {
                if(!checkStatus)
                {
                    iterator.remove();
                }
            }else if(status.equals("2"))
            {
                if(checkStatus)
                {
                    iterator.remove();
                }
            }

            next.put("checkStatus",checkStatus);
            next.put("userName",userName.get(id));
            next.put("pubishTime",pubishTime.substring(0,8));
        }
        ResponseHelper.printOut(response,arrayTo.toJSONString());
    }

    @RequestMapping(value="/loadPdm.do",method= RequestMethod.POST)
    public void loadPdm(HttpServletRequest request,
                    HttpServletResponse response) throws Exception
    {
        String appId = request.getParameter("appId");
        TDbPdm pdm = dbChangeService.loadDbPdm(appId);
        HashMap result = new HashMap();
        if(pdm==null)
        {
            result.put("exist",false);
        }else
        {
            String lock = pdm.getLock();
            String lockUserName = pdm.getLockUserId()!=null?pdm.getLockUserId().getUserName():"";

            result.put("exist",true);
            if("1".equals(lock))
            {
                result.put("isLock",true);
                result.put("lockUserName",lockUserName);
            }else
            {
                result.put("isLock",false);
            }
            TUser user = (TUser)request.getSession().getAttribute("user");
            if(user.getRole().equals("3"))
            {
                result.put("manage",true);
            }else
            {
                result.put("manage",false);
            }


        }
        ResponseHelper.printOut(response,JSON.toJSONString(result));
    }



    @RequestMapping(value="/loadDbChange.do",method= RequestMethod.POST)
    public void loadDbChange(Model model,HttpServletRequest request,
                    HttpServletResponse response) throws Exception
    {
        String changeId = request.getParameter("changeId");
        TDbChange change = dbChangeService.loadDbchange(changeId);
        List<TDbChecklist> checklist = change.getDbChecklistList();
        /**
         * private TUser checkUser;
         private TDbChange change;
         private String checkContent;
         private String checkStatus;
         private String checkTime;
         */
        Map result = new HashMap();

        List<Map> checkResultList = new ArrayList<Map>();
        result.put("dbChecklistList",checkResultList);
        result.put("changeLog",change.getChangeLog());
        result.put("db",change.getDb());
        result.put("checkStatus",change.geCheckStatus());
        result.put("module",change.getModule());
        result.put("script",change.getScript());
        result.put("dbType",change.getDbType());

        for(TDbChecklist check : checklist)
        {
            String userName = check.getCheckUser().getUserName();
            String checkContent = check.getCheckContent();
            String checkStatus = check.getCheckStatus();
            String checkTime = check.getCheckTime().substring(0,8);
            Map user = new HashMap();
            user.put("userName",userName);
            user.put("checkContent",checkContent);
            user.put("checkStatus",checkStatus);
            user.put("checkTime",checkTime);
            checkResultList.add(user);
        }
        ResponseHelper.printOut(response, JSON.toJSONString(result));
    }


    @Autowired
    AppService appService;

    /**
     * 图片上传
     *
     * @param request
     * @param response
     * @throws Exception
     */
    @RequestMapping(value = {"/uploadDb.do"}, method = RequestMethod.POST)
    public void upladDb(String appId,String changeId,HttpServletResponse response, HttpServletRequest request) {
        TApp app = null;
        try {
            app = appService.loadApp(appId);
            TUser user = (TUser)request.getSession().getAttribute("user");
            TDbPdm pdm = dbChangeService.isNotMyLock(app.getAppId(), user.getUserId());
            if(!StringUtils.isBlank(changeId)) {
                TDbChange change = dbChangeService.loadDbchange(changeId);
                if (change.geCheckStatus()) {
                    throw new RuntimeException("记录已经已经审批通过,不让修改");
                }
            }

            if(pdm != null)
            {
                throw new RuntimeException("记录已经被"+pdm.getLockUserId().getUserName()+"锁定");
            }
            String appcode = app.getAppCode();
            MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
            // 获得文件
            MultipartFile imgFile = multipartRequest.getFile("file1");
            if (imgFile == null || imgFile.getSize() == 0)
            {
                imgFile = multipartRequest.getFile("file2");
            }
            if (imgFile != null && imgFile.getSize() > 0) {
                // 获得文件名
                String filename = imgFile.getOriginalFilename();
                //获取上传文件类型的扩展名,先得到.的位置，再截取从.的下一个位置到文件的最后，最后得到扩展名
                String ext = filename.substring(filename.lastIndexOf("."), filename.length());
                String destfilename = "";
                String type="";
                if(ext.indexOf("pdm")!=-1)
                {
                    type = "pdm";
                    destfilename = appcode+".pdm";
                }
                if(ext.indexOf("sql")!=-1)
                {
                    type = "sql";
                    if(StringUtils.isBlank(changeId)) {
                        destfilename = dbChangeService.getVersion(appId) + ".sql";
                    }else
                    {
                        destfilename = dbChangeService.loadDbchange(changeId).getScript();
                    }
                }

                if (ext.toLowerCase().indexOf(".pdm") < 0 && ext.toLowerCase().indexOf(".sql")<0) {

                    //ResponseHelper.printOut(response, false, "只允许上传JAVA文件", "");
                    //return;
                    throw new RuntimeException("只允许上传PDM和SQL扩展名文件");
                } else {
                    // 重新命名文件名字
                    File path = new File(SysInit.path + File.separator + "db" + File.separator + appcode);
                    if (!path.exists()) {
                        path.mkdirs();
                    }
                    // 获得文件名
                    File source = new File(SysInit.path + File.separator + "db" + File.separator + appcode +"/"+
                            destfilename);
                    if ( source.exists() && ext.toLowerCase().indexOf(".pdm")!=-1 ) {
                        source.renameTo(new File(SysInit.path + File.separator + "db" + File.separator + appcode +"/"+
                                DateUtils.getDBString(new Date())+".pdm"));
                    }else
                    {
                        source.delete();
                    }
                    imgFile.transferTo(source);

                    if(source.getName().indexOf("sql")!=-1)
                    {
                        int code = SinoDetect.getInstance().detectEncoding(source);
                        if(code != Encoding.UTF8 && code !=Encoding.UTF8S && code !=Encoding.UTF8T && code!=Encoding.ASCII)
                        {

                            throw new RuntimeException("不能传非UTF-8编码的SQL脚本");
                        }
                        byte[] head = FileUtils.readFileToByteArray(source);
                        if(head[0]==-17 && head[1]==-69 && head[2] ==-65)
                        {
                            throw new RuntimeException("不能传UTF-8带BOM编码的SQL脚本");
                        }
                    }

                    String result = ResponseHelper.covert2Json(true, "", destfilename);
                    //result = "<script>parent.uploadDbSuccess(" + result + ",'" + app.getAppId() + "')</script>";
                    ResponseHelper.printOut(response, result);
                    return;
                }
            }
        } catch (Exception e) {
            logger.error("上传图片出错！", e);
            //ResponseHelper.printOut(response, false, "上传JAVA文件出错", "");
            String result = ResponseHelper.covert2Json(false, e.getMessage(), e.getMessage());
            //result = "<script>parent.uploadDbSuccess(" + result + ")</script>";
            ResponseHelper.printOut(response, result);
            return;
        }
    }


    @RequestMapping(value = {"/unlock.do"}, method = RequestMethod.POST)
    public void upladDb(String appId, HttpServletRequest request,HttpServletResponse response ) {
        dbChangeService.unlockPdm(appId);
        ResponseHelper.printOut(response, "");
    }




        @RequestMapping(value = {"/saveDbChange.do"}, method = RequestMethod.POST)
    public void saveDbChange(String appId,String changelog,String db,String changeId,String module,String dbType,
                             HttpServletResponse response, HttpServletRequest request)
    {
        try {
            TUser user = (TUser) request.getSession().getAttribute("user");
            changelog = request.getParameter("changelog");
            TDbPdm pdm = dbChangeService.isNotMyLock(new Integer(appId), user.getUserId());
            if (pdm != null) {
                String result = ResponseHelper.covert2Json(false, "pdm已经被" + pdm.getLockUserId().
                        getUserName() + "锁定", "");
                //result = "<script>parent.uploadDbSuccess(" + result + ")</script>";
                ResponseHelper.printOut(response, result);
                return;
            }
            dbChangeService.saveChange(appId, changelog, db, user.getUserId(),changeId,module,dbType);
            String result = ResponseHelper.covert2Json(true, "", "");
            //result = "<script>parent.uploadDbSuccess(" + result + ")</script>";
            ResponseHelper.printOut(response, result);
            return;
        }catch (Exception e)
        {
            logger.error("保存出错！", e);
            //ResponseHelper.printOut(response, false, "上传JAVA文件出错", "");
            String result = ResponseHelper.covert2Json(false, e.getMessage(), e.getMessage());
            //result = "<script>parent.uploadDbSuccess(" + result + ")</script>";
            ResponseHelper.printOut(response, result);
            return;
        }
    }
    @RequestMapping(value = {"/downloadPdm.do"}, method = RequestMethod.GET)
    public void downloadPdm(HttpServletResponse response, HttpServletRequest request) throws Exception {
        String appId = request.getParameter("appId");
        String lock = request.getParameter("lock");
        TApp app = appService.loadApp(appId);
        String fileName = app.getAppCode()+".pdm";
        if(!StringUtils.isBlank(lock))
        {
            TUser user = (TUser) request.getSession().getAttribute("user");
            dbChangeService.lockPdm(app.getAppId()+"",user);
        }

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment;"
                + " filename=" + new String(fileName.getBytes("UTF-8"), "ISO8859-1"));
        FileInputStream input = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        String fileFullPath = SysInit.path + File.separator + "db" + File.separator + app.getAppCode() +
                File.separator + fileName;

        try {
            input = new FileInputStream(fileFullPath);
            IOUtils.copy(input,outputStream);
        }catch (Exception e)
        {
            logger.error("",e);
            throw new RuntimeException("下载出错");
        }finally {
            IOUtils.closeQuietly(input);
        }

        OutputStream out = response.getOutputStream();
        response.setHeader("Content-Length", outputStream.size() + "");
        response.getOutputStream().write(outputStream.toByteArray());
        out.close();
    }

    @RequestMapping(value = {"/downloadDb.do"}, method = RequestMethod.GET)
    public void downloadJava(HttpServletResponse response, HttpServletRequest request) throws Exception {
        //application/octet-stream
        String changeId = request.getParameter("changeId");
        String name = request.getParameter("name");
        String lock = request.getParameter("lock");
        TDbChange change = dbChangeService.loadDbchange(changeId);
        String appCode = change.getAppId().getAppCode();
        String fileName = "";
        String downloadName = "";
        if(name.equals("pdm"))
        {
            fileName = appCode+".pdm";
            downloadName = fileName;
        }else
        {
            fileName = change.getScript();
            downloadName = change.getDb()+"."+change.getVersion().substring(0,8)+"_"+
                    change.getVersion().substring(8)+"_"+change.getModule()+"_"+change.getDbType()+".sql";
        }

        if(!StringUtils.isBlank(lock))
        {
            TUser user = (TUser) request.getSession().getAttribute("user");
            dbChangeService.lockPdm(change.getAppId().getAppId()+"",user);
        }

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment;"
                + " filename=" + new String(downloadName.getBytes("UTF-8"), "ISO8859-1"));
        FileInputStream input = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        String fileFullPath = SysInit.path + File.separator + "db" + File.separator + appCode +
                File.separator + fileName;

        try {
            input = new FileInputStream(fileFullPath);
            IOUtils.copy(input,outputStream);
        }catch (Exception e)
        {
            logger.error("",e);
            throw new RuntimeException("下载出错");
        }finally {
            IOUtils.closeQuietly(input);
        }

        OutputStream out = response.getOutputStream();
        response.setHeader("Content-Length", outputStream.size() + "");
        response.getOutputStream().write(outputStream.toByteArray());
        out.close();
    }


    @RequestMapping(value = {"/saveChangeCheck.do"}, method = RequestMethod.POST)
    public void saveChangeCheck(String changeId,String checkStatus,String checkContent,
                             HttpServletResponse response, HttpServletRequest request)
    {
        try {
            TUser user = (TUser) request.getSession().getAttribute("user");
            dbChangeService.saveCheck(changeId,checkStatus,checkContent,user);
            String result = ResponseHelper.covert2Json(true, "", "");
            ResponseHelper.printOut(response, result);
            return;
        }catch (Exception e)
        {
            logger.error("保存出错！", e);
            //ResponseHelper.printOut(response, false, "上传JAVA文件出错", "");
            String result = ResponseHelper.covert2Json(false, e.getMessage(), e.getMessage());
            //result = "<script>parent.uploadDbSuccess(" + result + ")</script>";
            ResponseHelper.printOut(response, result);
            return;
        }
    }
    @RequestMapping(value = {"/validateBatchDowlnload.do"}, method = RequestMethod.POST)
    public void validateBatchDowlnload(HttpServletResponse response, HttpServletRequest request) throws Exception {
        String appId = request.getParameter("appId");
        String begin = request.getParameter("begin");
        String end = request.getParameter("end");
        try {
            List<TDbChange> list = dbChangeService.getDownLoadList(appId, begin, end);
            if (list.size() == 0) {
                throw new RuntimeException("没有找到相应的版本");
            }
            String versions = "";
            for (TDbChange db : list) {
                if (!db.geCheckStatus()) {
                    versions += db.getVersion() + ",";
                }
            }
            if (!StringUtils.isBlank(versions)) {
                throw new RuntimeException("版本:" + versions + "未审批通过");
            }

            String result = ResponseHelper.covert2Json(true,"","");
            ResponseHelper.printOut(response, result);
            return;

        }catch (Exception e)
        {

            logger.error("校验出错！", e);
            String result = ResponseHelper.covert2Json(false, e.getMessage(), e.getMessage());
            ResponseHelper.printOut(response, result);
            return;
        }


    }

    @RequestMapping(value = {"/batchDlownload.do"}, method = RequestMethod.GET)
    public void batchDlownload(HttpServletResponse response, HttpServletRequest request) throws Exception {
        //application/octet-stream
        String appId = request.getParameter("appId");
        String begin = request.getParameter("begin");
        String end = request.getParameter("end");
        List<TDbChange> list = dbChangeService.getDownLoadList(appId,begin,end);
        String appCode = "";

        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment;"
                + " filename=" + new String((DateUtils.getDBString(new Date()).substring(0,8)+".zip").getBytes("UTF-8"), "ISO8859-1"));
        ZipOutputStream out = new ZipOutputStream(response.getOutputStream());
        out.setEncoding("GBK");
        for(TDbChange chabnge:list)
        {
            appCode = chabnge.getAppId().getAppCode();
            String fileFullPath = SysInit.path + File.separator + "db" + File.separator + appCode +
                    File.separator + chabnge.getScript();
            String string = chabnge.getDb()+"_"+chabnge.getScript();
            string = string.replaceAll("(\\s[\u4E00-\u9FA5]+)|([\u4E00-\u9FA5]+\\s)", "");
            out.putNextEntry(new ZipEntry(string));
            FileInputStream input = null;
            try {
                input = new FileInputStream(fileFullPath);
                IOUtils.copy(input,out);
            }catch (Exception e)
            {
                logger.error("",e);
                throw new RuntimeException("下载出错");
            }finally {
                IOUtils.closeQuietly(input);
            }
        }
        out.close();
    }

    @RequestMapping(value = {"/getScript.do"}, method = RequestMethod.POST)
    public void getJava(String changeId,
                        HttpServletResponse response, HttpServletRequest request) throws Exception {
        TDbChange change = dbChangeService.loadDbchange(changeId);
        String appCode = change.getAppId().getAppCode();
        String fileName = SysInit.path + File.separator + "db" + File.separator + appCode +
                File.separator + change.getScript();
        int code = SinoDetect.getInstance().detectEncoding(new File(fileName));
        String encode = "";
        if(code == Encoding.UTF8 || code ==Encoding.UTF8S || code ==Encoding.UTF8T)
        {
            encode = "UTF-8";
        }
        if(code == Encoding.GB2312 || code == Encoding.GBK || code == Encoding.GB18030)
        {
            encode = "GBK";
        }

        String result = FileUtils.readFileToString(new File(fileName),encode);
        result = result.replaceAll("<", "&lt;");
        result = result.replaceAll(">", "&gt;");
        result = result.replaceAll("\n", "<br />");
        ResponseHelper.printOut(response, true, "", result);
    }

    public static void main(String []a)throws Exception
    {
        File source = new File("/Users/criss/Downloads/WG_YW.sql");
        int code = SinoDetect.getInstance().detectEncoding(source);
        System.out.print(code);
        if(code != Encoding.UTF8 && code !=Encoding.UTF8S && code !=Encoding.UTF8T)
        {

            throw new RuntimeException("不能传非UTF-8编码的SQL脚本");
        }
    }


}
