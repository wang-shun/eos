/**
 * @(#)JsCreator
 * 版权声明 厦门畅享信息技术有限公司, 版权所有 违者必究
 *
 *<br> Copyright:  Copyright (c) 2016
 *<br> Company:厦门畅享信息技术有限公司
 *<br> @author ulyn
 *<br> 16-6-28 下午4:16
 *<br> @version 1.0
 *————————————————————————————————
 *修改记录
 *    修改者：
 *    修改时间：
 *    修改原因：
 *————————————————————————————————
 */
package com.sunsharing.eos.uddi.service.creator;

import com.sunsharing.eos.uddi.model.TApp;
import com.sunsharing.eos.uddi.model.TMethod;
import com.sunsharing.eos.uddi.model.TService;
import com.sunsharing.eos.uddi.model.TServiceVersion;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * <pre></pre>
 * <br>----------------------------------------------------------------------
 * <br> <b>功能描述:</b>
 * <br>
 * <br> 注意事项:
 * <br>
 * <br>
 * <br>----------------------------------------------------------------------
 * <br>
 */
public class JSCreator implements ICreator {

    Logger logger = Logger.getLogger(getClass());

    @Override
    public File create(String fileDir,TApp app, String v, List<TService> services) throws Exception{
        for (TService service : services) {
            createServiceFile(service, fileDir);
        }
        createAllServiceSingleFile(services, app, fileDir);
        return new File(fileDir);
    }

    private void createAllServiceSingleFile(List<TService> services, TApp app, String fileDir) throws IOException {
        String file = fileDir + "/"+ app.getAppCode() +"/all.js";
        StringBuilder sb = new StringBuilder("/** \n");
        sb.append("* " + app.getAppCode() + " - " + app.getAppName() + " \n");
        sb.append("* " + app.getCreatTime() + " \n*/\n");
        sb.append("\"use strict\";\n" +
                "(function (factory) {\n" +
                "    if (typeof define === 'function' && define.amd) {\n" +
                "        define([\"eos\"],factory);\n" +
                "    } else {\n" +
                "        factory(eos);\n" +
                "    }\n" +
                "}(function (eos) {\n");
        sb.append("\n" +
                "    var APP_ID = \""+ app.getAppCode() +"\";\n" +
                "\n" );
        for(TService service : services){
            TServiceVersion serviceVersion = service.getVersions().get(0);
            sb.append("    eos.registerService(APP_ID,\""+ service.getServiceCode() +"\")");
            for (TMethod method : serviceVersion.getMethods()) {

                String methodName = method.getMethodName();
                if (methodName.startsWith("void ")) {
                    methodName = methodName.replace("void ", "");
                }

                String paramsStr = method.getParams();
                ParamsResolver.InOutParameter inOutParameter = ParamsResolver.toInOutParams(paramsStr);

                sb.append("\n        .registerMethod(\""+ methodName +"\",\""+ method.getMethodVersion() +"\",");
                sb.append("[");
                for(int i=0;i< inOutParameter.getInParameters().size();i++){
                    ParamsResolver.InParameter inParameter = inOutParameter.getInParameters().get(i);
                    if(i!=0){
                        sb.append(",");
                    }
                    sb.append("\""+ inParameter.getName() +"\"" );
                }
                sb.append("])");
            }
            sb.append(";\n\n");
        }
        sb.append("    return eos[APP_ID];\n" +
                "}));");


        FileUtils.writeStringToFile(new File(file), sb.toString(), "utf-8");
    }

    public File createServiceFile(TService service,String path) throws Exception {
        String file = path + "/"+ service.getAppCode() +"/" + service.getServiceCode() + ".js";
        TServiceVersion serviceVersion = service.getVersions().get(0);
        StringBuilder sb = new StringBuilder("/** \n");
        sb.append("* " + service.getModule() + " - " + service.getServiceName() + " \n");
        sb.append("* " + service.getServiceCode() + " - " + serviceVersion.getVersionId() + " \n*/\n");

        sb.append("\"use strict\";\n" +
                "(function (factory) {\n" +
                "    if (typeof define === 'function' && define.amd) {\n" +
                "        define([\"eos\"],factory);\n" +
                "    } else {\n" +
                "        factory(eos);\n" +
                "    }\n" +
                "}(function (eos) {\n" +
                "\n" +
                "    var APP_ID = \""+ service.getAppCode() +"\",SERVICE_ID = \""+ service.getServiceCode() +"\";\n" +
                "\n" +
                "    eos.registerService(APP_ID,SERVICE_ID)");
        for (TMethod method : serviceVersion.getMethods()) {

            String methodName = method.getMethodName();
            if (methodName.startsWith("void ")) {
                methodName = methodName.replace("void ", "");
            }

            String paramsStr = method.getParams();
            ParamsResolver.InOutParameter inOutParameter = ParamsResolver.toInOutParams(paramsStr);

            sb.append("\n        .registerMethod(\""+ methodName +"\",\""+ method.getMethodVersion() +"\",");
            sb.append("[");
            for(int i=0;i< inOutParameter.getInParameters().size();i++){
                ParamsResolver.InParameter inParameter = inOutParameter.getInParameters().get(i);
                if(i!=0){
                    sb.append(",");
                }
                sb.append("\""+ inParameter.getName() +"\"" );
            }
            sb.append("])");
        }
        sb.append(";\n\n" +
                "    return eos[APP_ID][SERVICE_ID];\n" +
                "}));");

        File jsFile = new File(file);
        FileUtils.writeStringToFile(jsFile, sb.toString(), "utf-8");
        return jsFile;
    }

}

