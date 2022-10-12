/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.curator.framework.recipes.locks;

import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

public class StandardLockInternalsDriver implements LockInternalsDriver
{
    static private final Logger log = LoggerFactory.getLogger(StandardLockInternalsDriver.class);

    @Override
    public PredicateResults getsTheLock(CuratorFramework client, List<String> children, String sequenceNodeName, int maxLeases) throws Exception
    {
        int             ourIndex = children.indexOf(sequenceNodeName);  // 当前节点在这个子目录下的顺序
        validateOurIndex(sequenceNodeName, ourIndex);  // 验证
        // maxLeases是什么意思呢？每次允许几个客户端同时获取一个锁，默认肯定是最多允许一个客户端获取一把锁（maxLeases是实例化InterProcessMutex的时候赋值为1）
        // 什么情况下 getsTheLock 为 true？
        // 只有当 ourIndex 为0才可能是true，也就是在当前目录下，临时顺序节点的索引是0才算加锁成功
        // 换言之，只有排在目录的第一位才能加锁成功；当释放锁的时候会删除第一位临时顺序节点，这样的话才可以让下一位成功加锁
        boolean         getsTheLock = ourIndex < maxLeases;
        String          pathToWatch = getsTheLock ? null : children.get(ourIndex - maxLeases);  // 如果不是第一个节点，获取需要监听的前一个节点

        return new PredicateResults(pathToWatch, getsTheLock);
    }

    @Override
    public String createsTheLock(CuratorFramework client, String path, byte[] lockNodeBytes) throws Exception
    {
        String ourPath;
        if ( lockNodeBytes != null )
        {
            ourPath = client.create().creatingParentContainersIfNeeded().withProtection().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(path, lockNodeBytes);
        }
        else  // 创建临时顺序节点（如果父路径不存在，会先创建父路径）
        {
            ourPath = client.create().creatingParentContainersIfNeeded().withProtection().withMode(CreateMode.EPHEMERAL_SEQUENTIAL).forPath(path);
        }
        return ourPath;  //  /locks/lock_01/_c_UUID-lock-0000000000
    }


    @Override
    public String fixForSorting(String str, String lockName)
    {
        return standardFixForSorting(str, lockName);
    }

    public static String standardFixForSorting(String str, String lockName)
    {
        int index = str.lastIndexOf(lockName);
        if ( index >= 0 )
        {
            index += lockName.length();
            return index <= str.length() ? str.substring(index) : "";
        }
        return str;
    }

    static void validateOurIndex(String sequenceNodeName, int ourIndex) throws KeeperException
    {
        if ( ourIndex < 0 )
        {
            throw new KeeperException.NoNodeException("Sequential path not found: " + sequenceNodeName);
        }
    }
}
