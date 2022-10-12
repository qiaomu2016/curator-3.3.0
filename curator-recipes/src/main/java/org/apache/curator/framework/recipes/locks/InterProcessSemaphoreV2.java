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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.apache.curator.RetryLoop;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.WatcherRemoveCuratorFramework;
import org.apache.curator.framework.api.PathAndBytesable;
import org.apache.curator.framework.imps.CuratorFrameworkState;
import org.apache.curator.framework.recipes.shared.SharedCountListener;
import org.apache.curator.framework.recipes.shared.SharedCountReader;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.utils.PathUtils;
import org.apache.curator.utils.ThreadUtils;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * A counting semaphore that works across JVMs. All processes
 * in all JVMs that use the same lock path will achieve an inter-process limited set of leases.
 * Further, this semaphore is mostly "fair" - each user will get a lease in the order requested
 * (from ZK's point of view).
 * </p>
 * <p>
 * There are two modes for determining the max leases for the semaphore. In the first mode the
 * max leases is a convention maintained by the users of a given path. In the second mode a
 * {@link SharedCountReader} is used as the method for semaphores of a given path to determine
 * the max leases.
 * </p>
 * <p>
 * If a {@link SharedCountReader} is <b>not</b> used, no internal checks are done to prevent
 * Process A acting as if there are 10 leases and Process B acting as if there are 20. Therefore,
 * make sure that all instances in all processes use the same numberOfLeases value.
 * </p>
 * <p>
 * The various acquire methods return {@link Lease} objects that represent acquired leases. Clients
 * must take care to close lease objects  (ideally in a <code>finally</code>
 * block) else the lease will be lost. However, if the client session drops (crash, etc.),
 * any leases held by the client are automatically closed and made available to other clients.
 * </p>
 * <p>
 * Thanks to Ben Bangert (ben@groovie.org) for the algorithm used.
 * </p>
 *
 * 示例：
 * <pre>
 *     public static void main(String[] args) throws Exception {
 *         RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
 *         CuratorFramework client = CuratorFrameworkFactory.newClient(
 *                 "192.168.56.101:2181,192.168.56.102:2181,192.168.56.103:2181",
 *                 retryPolicy);
 *         client.start();
 *
 *         // 使用InterProcessSemaphoreMutex创建时，默认的信号量为1
 *         InterProcessLock semaphore = new InterProcessSemaphoreMutex(client, "/semaphore/semaphore_01");
 *         semaphore.acquire();
 *         Thread.sleep(30000);
 *         semaphore.release();
 *
 *         // 最后一个参数 用于指定信号量的大小，表示可以同时允许最多指定大小的客户端进行加锁
 *         InterProcessSemaphoreV2 semaphoreV2 = new InterProcessSemaphoreV2(client, "/semaphore/semaphore_02", 3);
 *         Lease lease = null;
 *         try {
 *             lease = semaphoreV2.acquire();
 *             Thread.sleep(30000);
 *         } finally {
 *             semaphoreV2.returnLease(lease);
 *         }
 *     }
 * </pre>
 */
public class InterProcessSemaphoreV2
{
    private final Logger log = LoggerFactory.getLogger(getClass());
    private final InterProcessMutex lock;
    private final WatcherRemoveCuratorFramework client;
    private final String leasesPath;
    private final Watcher watcher = new Watcher()
    {
        @Override
        public void process(WatchedEvent event)
        {
            notifyFromWatcher();
        }
    };

    private volatile byte[] nodeData;
    private volatile int maxLeases;

    private static final String LOCK_PARENT = "locks";
    private static final String LEASE_PARENT = "leases";
    private static final String LEASE_BASE_NAME = "lease-";
    public static final Set<String> LOCK_SCHEMA = Sets.newHashSet(
            LOCK_PARENT,
            LEASE_PARENT
    );

    /**
     * @param client    the client
     * @param path      path for the semaphore
     * @param maxLeases the max number of leases to allow for this instance
     */
    public InterProcessSemaphoreV2(CuratorFramework client, String path, int maxLeases)
    {
        this(client, path, maxLeases, null);
    }

    /**
     * @param client the client
     * @param path   path for the semaphore
     * @param count  the shared count to use for the max leases
     */
    public InterProcessSemaphoreV2(CuratorFramework client, String path, SharedCountReader count)
    {
        this(client, path, 0, count);
    }

    private InterProcessSemaphoreV2(CuratorFramework client, String path, int maxLeases, SharedCountReader count)
    {
        // 以InterProcessSemaphoreV2 semaphoreV2 = new InterProcessSemaphoreV2(client, "/semaphore/semaphore_02", 3);为例
        this.client = client.newWatcherRemoveCuratorFramework();
        path = PathUtils.validatePath(path);  // /semaphore/semaphore_02
        // 创建一个可重入锁对象 InterProcessMutex， 锁路径为： /semaphore/semaphore_02/locks
        lock = new InterProcessMutex(client, ZKPaths.makePath(path, LOCK_PARENT));
        this.maxLeases = (count != null) ? count.getCount() : maxLeases;  // count为空，取值为3
        leasesPath = ZKPaths.makePath(path, LEASE_PARENT); // lease路径：/semaphore/semaphore_02/leases

        if ( count != null )
        {
            count.addListener
                (
                    new SharedCountListener()
                    {
                        @Override
                        public void countHasChanged(SharedCountReader sharedCount, int newCount) throws Exception
                        {
                            InterProcessSemaphoreV2.this.maxLeases = newCount;
                            notifyFromWatcher();
                        }

                        @Override
                        public void stateChanged(CuratorFramework client, ConnectionState newState)
                        {
                            // no need to handle this here - clients should set their own connection state listener
                        }
                    }
                );
        }
    }

    /**
     * Set the data to put for the node created by this semaphore. This must be called prior to calling one
     * of the acquire() methods.
     *
     * @param nodeData node data
     */
    public void setNodeData(byte[] nodeData)
    {
        this.nodeData = (nodeData != null) ? Arrays.copyOf(nodeData, nodeData.length) : null;
    }

    /**
     * Return a list of all current nodes participating in the semaphore
     *
     * @return list of nodes
     * @throws Exception ZK errors, interruptions, etc.
     */
    public Collection<String> getParticipantNodes() throws Exception
    {
        return client.getChildren().forPath(leasesPath);
    }

    /**
     * Convenience method. Closes all leases in the given collection of leases
     *
     * @param leases leases to close
     */
    public void returnAll(Collection<Lease> leases)
    {
        for ( Lease l : leases )
        {
            CloseableUtils.closeQuietly(l);
        }
    }

    /**
     * Convenience method. Closes the lease
     *
     * @param lease lease to close
     */
    public void returnLease(Lease lease)
    {
        CloseableUtils.closeQuietly(lease);
    }

    /**
     * <p>Acquire a lease. If no leases are available, this method blocks until either the maximum
     * number of leases is increased or another client/process closes a lease.</p>
     * <p>The client must close the lease when it is done with it. You should do this in a
     * <code>finally</code> block.</p>
     *
     * @return the new lease
     * @throws Exception ZK errors, interruptions, etc.
     */
    public Lease acquire() throws Exception
    {
        Collection<Lease> leases = acquire(1, 0, null);
        return leases.iterator().next();
    }

    /**
     * <p>Acquire <code>qty</code> leases. If there are not enough leases available, this method
     * blocks until either the maximum number of leases is increased enough or other clients/processes
     * close enough leases.</p>
     * <p>The client must close the leases when it is done with them. You should do this in a
     * <code>finally</code> block. NOTE: You can use {@link #returnAll(Collection)} for this.</p>
     *
     * @param qty number of leases to acquire
     * @return the new leases
     * @throws Exception ZK errors, interruptions, etc.
     */
    public Collection<Lease> acquire(int qty) throws Exception
    {
        return acquire(qty, 0, null);
    }

    /**
     * <p>Acquire a lease. If no leases are available, this method blocks until either the maximum
     * number of leases is increased or another client/process closes a lease. However, this method
     * will only block to a maximum of the time parameters given.</p>
     * <p>The client must close the lease when it is done with it. You should do this in a
     * <code>finally</code> block.</p>
     *
     * @param time time to wait
     * @param unit time unit
     * @return the new lease or null if time ran out
     * @throws Exception ZK errors, interruptions, etc.
     */
    public Lease acquire(long time, TimeUnit unit) throws Exception
    {
        Collection<Lease> leases = acquire(1, time, unit);
        return (leases != null) ? leases.iterator().next() : null;
    }

    /**
     * <p>Acquire <code>qty</code> leases. If there are not enough leases available, this method
     * blocks until either the maximum number of leases is increased enough or other clients/processes
     * close enough leases. However, this method will only block to a maximum of the time
     * parameters given. If time expires before all leases are acquired, the subset of acquired
     * leases are automatically closed.</p>
     * <p>The client must close the leases when it is done with them. You should do this in a
     * <code>finally</code> block. NOTE: You can use {@link #returnAll(Collection)} for this.</p>
     *
     * @param qty  number of leases to acquire
     * @param time time to wait
     * @param unit time unit
     * @return the new leases or null if time ran out
     * @throws Exception ZK errors, interruptions, etc.
     */
    public Collection<Lease> acquire(int qty, long time, TimeUnit unit) throws Exception
    {
        long startMs = System.currentTimeMillis();
        boolean hasWait = (unit != null);
        long waitMs = hasWait ? TimeUnit.MILLISECONDS.convert(time, unit) : 0;

        Preconditions.checkArgument(qty > 0, "qty cannot be 0");

        ImmutableList.Builder<Lease> builder = ImmutableList.builder();
        boolean success = false;
        try
        {
            while ( qty-- > 0 )
            {
                int retryCount = 0;
                long startMillis = System.currentTimeMillis();
                boolean isDone = false;
                while ( !isDone )
                {
                    switch ( internalAcquire1Lease(builder, startMs, hasWait, waitMs) )
                    {
                        case CONTINUE:
                        {
                            isDone = true;
                            break;
                        }

                        case RETURN_NULL:
                        {
                            return null;
                        }

                        case RETRY_DUE_TO_MISSING_NODE:
                        {
                            // gets thrown by internalAcquire1Lease when it can't find the lock node
                            // this can happen when the session expires, etc. So, if the retry allows, just try it all again
                            if ( !client.getZookeeperClient().getRetryPolicy().allowRetry(retryCount++, System.currentTimeMillis() - startMillis, RetryLoop.getDefaultRetrySleeper()) )
                            {
                                throw new KeeperException.NoNodeException("Sequential path not found - possible session loss");
                            }
                            // try again
                            break;
                        }
                    }
                }
            }
            success = true;
        }
        finally
        {
            if ( !success )
            {
                returnAll(builder.build());
            }
        }

        return builder.build();
    }

    private enum InternalAcquireResult
    {
        CONTINUE,
        RETURN_NULL,
        RETRY_DUE_TO_MISSING_NODE
    }

    static volatile CountDownLatch debugAcquireLatch = null;
    static volatile CountDownLatch debugFailedGetChildrenLatch = null;

    private InternalAcquireResult internalAcquire1Lease(ImmutableList.Builder<Lease> builder, long startMs, boolean hasWait, long waitMs) throws Exception
    {
        if ( client.getState() != CuratorFrameworkState.STARTED )
        {
            return InternalAcquireResult.RETURN_NULL;
        }

        // 调用InterProcessMutex进行加锁，会创建临时顺序节点：/semaphores/semaphore_01/locks/_c_UUID-lock-0000000000
        if ( hasWait )
        {
            long thisWaitMs = getThisWaitMs(startMs, waitMs);
            if ( !lock.acquire(thisWaitMs, TimeUnit.MILLISECONDS) )
            {
                return InternalAcquireResult.RETURN_NULL;
            }
        }
        else
        {
            lock.acquire();
        }

        Lease lease = null;

        try
        {
            // 创建lease的临时顺序节点：/semaphores/semaphore_01/leases/_c_UUID-lease-0000000000
            PathAndBytesable<String> createBuilder = client.create().creatingParentContainersIfNeeded().withProtection().withMode(CreateMode.EPHEMERAL_SEQUENTIAL);
            String path = (nodeData != null) ? createBuilder.forPath(ZKPaths.makePath(leasesPath, LEASE_BASE_NAME), nodeData) : createBuilder.forPath(ZKPaths.makePath(leasesPath, LEASE_BASE_NAME));
            String nodeName = ZKPaths.getNodeFromPath(path);  // 获取节点的名称： _c_UUID-lease-0000000000
            lease = makeLease(path);  // 创建一个Lease对象，包含了lease节点的名称、数据以及删除当前节点的close()方法

            if ( debugAcquireLatch != null )
            {
                debugAcquireLatch.await();
            }

            // 创建了一个Lease，这个Lease对象其实就代表了客户端A成功加的一个semaphore的这个锁，他先尝试成功加锁，如果成功加锁了以后，他就可以创建一个这个锁对应的一个lease，
            // 比如semaphore一共可以允许最多3个客户端同时加锁，应该就会有一个判断逻辑，就是说当前/semaphores/semaphore_02/leases目录下的节点的数量是否<=3
            // 如果是<=3的话，此时就会release释放掉之前加的那个/semaphores/semaphore_02/locks锁

            try
            {
                synchronized(this)
                {
                    for(;;)
                    {    
                        List<String> children;
                        try
                        {
                            // 获到目录 /semaphores/semaphore_01/leases 下的所有的节点，并对所有的节点进行监听
                            children = client.getChildren().usingWatcher(watcher).forPath(leasesPath);
                        }
                        catch ( Exception e )
                        {
                            if ( debugFailedGetChildrenLatch != null )
                            {
                                debugFailedGetChildrenLatch.countDown();
                            }
                            returnLease(lease); // otherwise the just created ZNode will be orphaned causing a dead lock
                            throw e;
                        }
                        if ( !children.contains(nodeName) )
                        {
                            log.error("Sequential path not found: " + path);
                            returnLease(lease);
                            return InternalAcquireResult.RETRY_DUE_TO_MISSING_NODE;
                        }
    
                        if ( children.size() <= maxLeases )  // 如果节点的数据少于等于信号量大小，直接退出循环，表示允许当前客户端申请资源
                        {
                            break;
                        }
                        // 如果节点的大小已经大于信号量的大小，此时会调用wait阻塞当前线程
                        if ( hasWait )
                        {
                            long thisWaitMs = getThisWaitMs(startMs, waitMs);
                            if ( thisWaitMs <= 0 )
                            {
                                returnLease(lease);
                                return InternalAcquireResult.RETURN_NULL;
                            }
                            wait(thisWaitMs);
                        }
                        else
                        {
                            wait();
                        }
                    }
                }
            }
            finally
            {
                client.removeWatchers();  // for循环结束，移除监听器
            }
        }
        finally
        {
            lock.release();  // 释放锁，此时 下一个线程就可以过来加锁了
        }
        builder.add(Preconditions.checkNotNull(lease));
        return InternalAcquireResult.CONTINUE;
    }

    private long getThisWaitMs(long startMs, long waitMs)
    {
        long elapsedMs = System.currentTimeMillis() - startMs;
        return waitMs - elapsedMs;
    }

    private Lease makeLease(final String path)
    {
        return new Lease()
        {
            @Override
            public void close() throws IOException
            {
                try
                {
                    client.delete().guaranteed().forPath(path);
                }
                catch ( KeeperException.NoNodeException e )
                {
                    log.warn("Lease already released", e);
                }
                catch ( Exception e )
                {
                    ThreadUtils.checkInterrupted(e);
                    throw new IOException(e);
                }
            }

            @Override
            public byte[] getData() throws Exception
            {
                return client.getData().forPath(path);
            }

            @Override
            public String getNodeName() {
                return ZKPaths.getNodeFromPath(path);
            }
        };
    }

    private synchronized void notifyFromWatcher()
    {
        notifyAll();
    }
}
