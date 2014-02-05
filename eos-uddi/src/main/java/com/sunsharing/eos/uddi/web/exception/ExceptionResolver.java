package com.sunsharing.eos.uddi.web.exception;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.sunsharing.eos.uddi.web.common.BaseController;
import com.sunsharing.eos.uddi.web.common.ResponseHelper;
import org.apache.log4j.Logger;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

public class ExceptionResolver extends BaseController implements HandlerExceptionResolver{
	Logger log = Logger.getLogger(ExceptionResolver.class);

    public ModelAndView resolveException(HttpServletRequest request, HttpServletResponse response, Object object, Exception ex) {
        log.error("Handle exception:" + ex.getClass().getName(), ex);
        String accept = request.getHeader("Accept");
        accept = accept==null?"":accept.toLowerCase();
        if(ex instanceof AjaxException||accept.indexOf("json")!=-1||accept.equals("*/*")){
            String errorMsg = ex.getMessage();
            if(errorMsg==null){
                errorMsg = "内部处理异常！";
            }
            ResponseHelper.printOut(response,false,errorMsg,"");
            return null;
        }else if(ex instanceof org.springframework.web.multipart.MaxUploadSizeExceededException){
            //此处属于文件大小超过配置的文件大小限制
            ResponseHelper.printOut(response,false,"文件超过系统预定义大小，请重新选择！","");
            return null;
        }else {
            ModelAndView model = new ModelAndView("errors/500");
            model.addObject("status", false);
            model.addObject("message", ex.getMessage());
            return model;
        }
    }
}
