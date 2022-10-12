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

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.MoreExecutors;
import org.apache.curator.framework.CuratorFramework;
import java.io.IOException;
import java.util.Collection;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.curator.utils.PathUtils;

/**
 * 可重入的独占锁
 * A re-entrant mutex that works across JVMs. Uses Zookeeper to hold the lock. All processes in all JVMs that
 * use the same lock path will achieve an inter-process critical section. Further, this mutex is
 * "fair" - each user will get the mutex in the order requested (from ZK's point of view)
 *
 * 示例：
 * <pre>
 * public class CuratorDemo {
 *     public static void main(String[] args) throws Exception {
 *         RetryPolicy retryPolicy = new ExponentialBackoffRetry(1000, 3);
 *         CuratorFramework client = CuratorFrameworkFactory.newClient(
 *                 "192.168.56.101:2181,192.168.56.102:2181,192.168.56.103:2181",
 *                 retryPolicy);
 *         client.start();
 *
 *         InterProcessLock lock = new InterProcessMutex(client,"/locks/lock_01");
 *         lock.acquire();
 *         Thread.sleep(30000);
 *         lock.release();
 *     }
 * }
 * </pre>
 */
public class InterProcessMutex implements InterProcessLock, Revocable<InterProcessMutex>
{
    private final LockInternals internals;
    private final String basePath;  // /locks/lock_01

    private final ConcurrentMap<Thread, LockData> threadData = Maps.newConcurrentMap();

    private static class LockData
    {
        final Thread owningThread;  // 当前线程
        final String lockPath;  // 锁的路径
        final AtomicInteger lockCount = new AtomicInteger(1); // 当前线程加锁的次数

        private LockData(Thread owningThread, String lockPath)
        {
            this.owningThread = owningThread;
            this.lockPath = lockPath;
        }
    }

    private static final String LOCK_NAME = "lock-";

    /**
     * @param client client
     * @param path   the path to lock
     */
    public InterProcessMutex(CuratorFramework client, String path)
    {
        this(client, path, new StandardLockInternalsDriver());
    }

    /**
     * @param client client
     * @param path   the path to lock
     * @param driver lock driver
     */
    public InterProcessMutex(CuratorFramework client, String path, LockInternalsDriver driver)
    {
        this(client, path, LOCK_NAME, 1, driver);
    }

    /**
     * Acquire the mutex - blocking until it's available. Note: the same thread
     * can call acquire re-entrantly. Each call to acquire must be balanced by a call
     * to {@link #release()}
     *
     * @throws Exception ZK errors, connection interruptions
     */
    @Override
    public void acquire() throws Exception
    {
        if ( !internalLock(-1, null) )
        {
            throw new IOException("Lost connection while trying to acquire lock: " + basePath);
        }
    }

    /**
     * Acquire the mutex - blocks until it's available or the given time expires. Note: the same thread
     * can call acquire re-entrantly. Each call to acquire that returns true must be balanced by a call
     * to {@link #release()}
     *
     * @param time time to wait
     * @param unit time unit
     * @return true if the mutex was acquired, false if not
     * @throws Exception ZK errors, connection interruptions
     */
    @Override
    public boolean acquire(long time, TimeUnit unit) throws Exception
    {
        return internalLock(time, unit);
    }

    /**
     * Returns true if the mutex is acquired by a thread in this JVM
     *
     * @return true/false
     */
    @Override
    public boolean isAcquiredInThisProcess()
    {
        return (threadData.size() > 0);
    }

    /**
     * Perform one release of the mutex if the calling thread is the same thread that acquired it. If the
     * thread had made multiple calls to acquire, the mutex will still be held when this method returns.
     *
     * @throws Exception ZK errors, interruptions, current thread does not own the lock
     */
    @Override
    public void release() throws Exception
    {
        /*
            Note on concurrency: a given lockData instance
            can be only acted on by a single thread so locking isn't necessary
         */

        Thread currentThread = Thread.currentThread();  // 获取当前线程
        LockData lockData = threadData.get(currentThread); // 获取当前线程绑定的锁相关的数据
        if ( lockData == null )
        {
            throw new IllegalMonitorStateException("You do not own the lock: " + basePath);
        }
        // 存在当前线程对应的锁（可重入锁）,当前线程的加锁次数-1
        int newLockCount = lockData.lockCount.decrementAndGet();
        if ( newLockCount > 0 )
        {
            return;
        }
        if ( newLockCount < 0 )
        {
            throw new IllegalMonitorStateException("Lock count has gone negative for lock: " + basePath);
        }
        try
        {
            internals.releaseLock(lockData.lockPath);  // 加锁次数为0，直接释放锁
        }
        finally
        {
            threadData.remove(currentThread);  // 移除当前线程对应的元素
        }
    }

    /**
     * Return a sorted list of all current nodes participating in the lock
     *
     * @return list of nodes
     * @throws Exception ZK errors, interruptions, etc.
     */
    public Collection<String> getParticipantNodes() throws Exception
    {
        return LockInternals.getParticipantNodes(internals.getClient(), basePath, internals.getLockName(), internals.getDriver());
    }

    @Override
    public void makeRevocable(RevocationListener<InterProcessMutex> listener)
    {
        makeRevocable(listener, MoreExecutors.sameThreadExecutor());
    }

    @Override
    public void makeRevocable(final RevocationListener<InterProcessMutex> listener, Executor executor)
    {
        internals.makeRevocable(new RevocationSpec(executor, new Runnable()
            {
                @Override
                public void run()
                {
                    listener.revocationRequested(InterProcessMutex.this);
                }
            }));
    }

    InterProcessMutex(CuratorFramework client, String path, String lockName, int maxLeases, LockInternalsDriver driver)
    {
        basePath = PathUtils.validatePath(path);
        internals = new LockInternals(client, driver, path, lockName, maxLeases);
    }

    boolean isOwnedByCurrentThread()
    {
        LockData lockData = threadData.get(Thread.currentThread());
        return (lockData != null) && (lockData.lockCount.get() > 0);
    }

    protected byte[] getLockNodeBytes()
    {
        return null;
    }

    protected String getLockPath()
    {
        LockData lockData = threadData.get(Thread.currentThread());
        return lockData != null ? lockData.lockPath : null;
    }

    private boolean internalLock(long time, TimeUnit unit) throws Exception
    {
        /*
           Note on concurrency: a given lockData instance
           can be only acted on by a single thread so locking isn't necessary
        */

        Thread currentThread = Thread.currentThread();  // 获取当前线程

        LockData lockData = threadData.get(currentThread);  // 获取当前线程绑定的锁相关的数据
        if ( lockData != null )  // 存在当前线程对应的锁（可重入锁）
        {
            // re-entering
            lockData.lockCount.incrementAndGet();  // 当前线程的加锁次数+1
            return true;
        }
        // 当前线程还未加锁，尝试加锁
        String lockPath = internals.attemptLock(time, unit, getLockNodeBytes());
        if ( lockPath != null )  // 加锁成功
        {
            LockData newLockData = new LockData(currentThread, lockPath);  // 构建锁的数据对象
            threadData.put(currentThread, newLockData);     // 放入集合中
            return true;
        }

        return false;
    }
}
