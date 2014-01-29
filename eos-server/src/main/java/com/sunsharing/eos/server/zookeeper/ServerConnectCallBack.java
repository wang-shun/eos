package com.sunsharing.eos.server.zookeeper;

import com.alibaba.fastjson.JSONObject;
import com.sunsharing.eos.common.zookeeper.PathConstant;
import com.sunsharing.eos.common.zookeeper.ZookeeperCallBack;
import com.sunsharing.eos.common.zookeeper.ZookeeperUtils;
import org.apache.log4j.Logger;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

/**
 * Created by criss on 14-1-27.
 */
public class ServerConnectCallBack implements ZookeeperCallBack {
    Logger logger = Logger.getLogger(ServerConnectCallBack.class);
    @Override
    public void afterConnect(WatchedEvent event) {
        try
        {
            logger.info("登录成功了开始调用回调");
            ZookeeperUtils utils = ZookeeperUtils.getInstance();
            utils.watchNode(PathConstant.EOS_STATE);

            ServiceRegister serviceRegister = ServiceRegister.getInstance();
            //先注册EOSID可以多个
            serviceRegister.registerEos("criss");

            //你可以在这里注册服务下面是示例
            JSONObject obj = new JSONObject();
            obj.put("appId","appId");
            obj.put("serviceId","serveId");
            obj.put("version", "1.0");

            serviceRegister.registerService("criss",obj.toJSONString());

//            utils.printNode("/");
        }catch (Exception e)
        {
            logger.error("初始化EOS出错",e);
        }
    }

    @Override
    public void watchNodeChange(WatchedEvent event) {
        ZookeeperUtils utils = ZookeeperUtils.getInstance();
        try
        {

            utils.watchNode(event.getPath());
        }catch (Exception e)
        {
            logger.error("初始化EOS出错",e);
        }

        if(event.getType()== Watcher.Event.EventType.NodeCreated)
        {
            if(event.getPath().startsWith(PathConstant.EOS_STATE))
            {
                String eos = event.getPath().substring(event.getPath().lastIndexOf("/")+1);
                logger.info("有EOS:"+eos+"上线了，考虑是否需要注册");
            }
        }
        if(event.getType()== Watcher.Event.EventType.NodeDeleted)
        {
            if(event.getPath().startsWith(PathConstant.EOS_STATE))
            {
                String eos = event.getPath().substring(event.getPath().lastIndexOf("/")+1);
                logger.info("有EOS:"+eos+"下线了，考虑是否做什么");
            }
        }

    }
}