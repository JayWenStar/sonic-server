/*
 *  Copyright (C) [SonicCloudOrg] Sonic Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.cloud.sonic.controller.zookeeper;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.CuratorCache;
import org.apache.curator.framework.recipes.cache.CuratorCacheListener;
import org.cloud.sonic.common.models.domain.Agents;
import org.cloud.sonic.common.models.interfaces.AgentStatus;
import org.cloud.sonic.common.services.AgentsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注入的报错无视就好，实际上是能找到的
 *
 * @author JayWenStar
 * @date 2022/4/10 12:20 上午
 */
@Configuration
@Slf4j
public class AgentStatusListener {

    @Bean
    public boolean agentsStatusListener(CuratorFramework curatorFramework, AgentsService agentsService) {
        String path = "/sonic-agent";
        CuratorCache curatorCache = CuratorCache.build(curatorFramework, path);
        curatorCache.listenable().addListener((type, oldData, data) -> {
            if (CuratorCacheListener.Type.NODE_DELETED == type) {
                String agentJson = new String(oldData.getData());
                Agents agents = JSON.parseObject(agentJson, Agents.class);
                agentsService.offLine(agents);
            }
            if (CuratorCacheListener.Type.NODE_CREATED == type) {
                if (data.getPath().equals(path)) {
                    return;
                }
                int agentId = Integer.parseInt(data.getPath().split("/")[2]);
                Agents agents = agentsService.findById(agentId);
                if (agents == null) {
                    return;
                }
                boolean online = agentsService.checkOnline(agents);
                if (!online) {
                    // server -> agent failed
                    agents.setStatus(AgentStatus.S2AE);
                    agentsService.updateAgentsByLockVersion(agents);
                }
                // online说明agent启动了，设备状态会同步过来，这里不做设备状态检查
            }
        });
        curatorCache.start();
        log.info("start watch /sonic-agent");
        return true;
    }


}
