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

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.curator.framework.CuratorFramework;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * <p>
 *    A re-entrant read/write mutex that works across JVMs. Uses Zookeeper to hold the lock. All processes
 *    in all JVMs that use the same lock path will achieve an inter-process critical section. Further, this mutex is
 *    "fair" - each user will get the mutex in the order requested (from ZK's point of view).
 * </p>
 *
 * <p>
 *    A read write lock maintains a pair of associated locks, one for read-only operations and one
 *    for writing. The read lock may be held simultaneously by multiple reader processes, so long as
 *    there are no writers. The write lock is exclusive.
 * </p>
 *
 * <p>
 *    <b>Reentrancy</b><br>
 *    This lock allows both readers and writers to reacquire read or write locks in the style of a
 *    re-entrant lock. Non-re-entrant readers are not allowed until all write locks held by the
 *    writing thread/process have been released. Additionally, a writer can acquire the read lock, but not
 *    vice-versa. If a reader tries to acquire the write lock it will never succeed.<br><br>
 *
 *    <b>Lock downgrading</b><br>
 *    Re-entrancy also allows downgrading from the write lock to a read lock, by acquiring the write
 *    lock, then the read lock and then releasing the write lock. However, upgrading from a read
 *    lock to the write lock is not possible.
 * </p>
 */
public class InterProcessReadWriteLock
{
    private final InterProcessMutex readMutex;
    private final InterProcessMutex writeMutex;

    // must be the same length. LockInternals depends on it
    private static final String READ_LOCK_NAME  = "__READ__";
    private static final String WRITE_LOCK_NAME = "__WRIT__";

    private static class SortingLockInternalsDriver extends StandardLockInternalsDriver
    {
        @Override
        public final String fixForSorting(String str, String lockName)
        {
            str = super.fixForSorting(str, READ_LOCK_NAME);
            str = super.fixForSorting(str, WRITE_LOCK_NAME);
            return str;
        }
    }

    private static class InternalInterProcessMutex extends InterProcessMutex
    {
        private final String lockName;
        private final byte[] lockData;

        InternalInterProcessMutex(CuratorFramework client, String path, String lockName, byte[] lockData, int maxLeases, LockInternalsDriver driver)
        {
            super(client, path, lockName, maxLeases, driver);
            this.lockName = lockName;
            this.lockData = lockData;
        }

        @Override
        public Collection<String> getParticipantNodes() throws Exception
        {
            Collection<String>  nodes = super.getParticipantNodes();
            Iterable<String>    filtered = Iterables.filter
            (
                nodes,
                new Predicate<String>()
                {
                    @Override
                    public boolean apply(String node)
                    {
                        return node.contains(lockName);
                    }
                }
            );
            return ImmutableList.copyOf(filtered);
        }

        @Override
        protected byte[] getLockNodeBytes()
        {
            return lockData;
        }
    }

  /**
    * @param client the client
    * @param basePath path to use for locking
    */
    public InterProcessReadWriteLock(CuratorFramework client, String basePath)
    {
        this(client, basePath, null);
    }

  /**
    * @param client the client
    * @param basePath path to use for locking
    * @param lockData the data to store in the lock nodes
    */
    public InterProcessReadWriteLock(CuratorFramework client, String basePath, byte[] lockData)
    {
        lockData = (lockData == null) ? null : Arrays.copyOf(lockData, lockData.length);

        writeMutex = new InternalInterProcessMutex
        (
            client,
            basePath,
            WRITE_LOCK_NAME,
            lockData,
            1,   // 写锁，同时只允许一个客户端加锁成功
            new SortingLockInternalsDriver()
            {
                @Override  // 锁获取的逻辑和InterProcessMutex一样，即临时顺序节点最小的一个节点才能成功获取锁
                public PredicateResults getsTheLock(CuratorFramework client, List<String> children, String sequenceNodeName, int maxLeases) throws Exception
                {
                    return super.getsTheLock(client, children, sequenceNodeName, maxLeases);
                }
            }
        );

        readMutex = new InternalInterProcessMutex
        (
            client,
            basePath,
            READ_LOCK_NAME,
            lockData,
            Integer.MAX_VALUE,  // 读锁，可以认为不限客户端（Integer类型的最大值）
            new SortingLockInternalsDriver()
            {
                @Override  // 重写获取锁的逻辑
                public PredicateResults getsTheLock(CuratorFramework client, List<String> children, String sequenceNodeName, int maxLeases) throws Exception
                {
                    return readLockPredicate(children, sequenceNodeName);
                }
            }
        );
    }

    /**
     * Returns the lock used for reading.
     *
     * @return read lock
     */
    public InterProcessMutex     readLock()
    {
        return readMutex;
    }

    /**
     * Returns the lock used for writing.
     *
     * @return write lock
     */
    public InterProcessMutex     writeLock()
    {
        return writeMutex;
    }

    private PredicateResults readLockPredicate(List<String> children, String sequenceNodeName) throws Exception
    {
        if ( writeMutex.isOwnedByCurrentThread() )  // 如果当前线程已经加了写锁，再加读锁时，直接返回加锁成功
        {
            return new PredicateResults(null, true);
        }

        int         index = 0;
        int         firstWriteIndex = Integer.MAX_VALUE;
        int         ourIndex = -1;
        for ( String node : children )
        {
            if ( node.contains(WRITE_LOCK_NAME) )  // 如果其它线程已经加了写锁
            {
                firstWriteIndex = Math.min(index, firstWriteIndex); // 再加读锁时，firstWriteIndex变成0，ourIndex变成1
            }
            else if ( node.startsWith(sequenceNodeName) )
            {
                ourIndex = index;  // 获取当前节点在所有节点的位置（索引）
                break;
            }

            ++index;
        }

        StandardLockInternalsDriver.validateOurIndex(sequenceNodeName, ourIndex);

        boolean     getsTheLock = (ourIndex < firstWriteIndex); // 只要当前节点<Integer.MAX_VALUE就表示加锁成功
        String      pathToWatch = getsTheLock ? null : children.get(firstWriteIndex);
        return new PredicateResults(pathToWatch, getsTheLock);
    }
}

/*
 * 1）读锁+读锁
 * firstWriteIndex：Integer.MAX_VALUE（2147483647）
 * 加读锁的逻辑非常简单，如果N多个客户端同时加读锁，有没有问题？一定没问题，每次去加一个读锁的时候，都是在/locks/lock_01目录下创建一个顺序节点
 * 然后呢，获取一个顺序节点在/locks/locks_01目录下的位置（索引）,只要< Integer.MAX_VALUE这个值就可以了,所以Integer.MAX_VALUE客户端同时加读锁都是没问题的
 * N多个客户端同时加读锁，肯定是不会互斥的，满足读写锁的逻辑
 *
 * 2）读锁+写锁
 * /locks/lock_01目录下，此时已经有了一个顺序节点，有了N个读锁的顺序节点
 * /locks/lock_01/_c_0548a389-3307-4134-9551-088d305b86c7-__READ__0000000003 就代表了说一个客户端加了读锁
 *
 * 加写锁的时候，上来会不分青红皂白，直接在/locks/locks_01目录下创建一个__WRITE__的写锁的顺序节点：/locks/lock_01/_c_73b60882-9361-4fb7-8420-a8d4911d2c99-__WRIT__0000000005
 * 此时/locks/lock_01目录下，之前已经有人加过一个读锁了，此时又往里面写了一个写锁的顺序节点：[_c_13bf63d6-43f3-4c2f-ba98-07a641d351f2-__READ__0000000004, _c_73b60882-9361-4fb7-8420-a8d4911d2c99-__WRIT__0000000005]
 * 写锁节点在children里是排在第二位的，index是1
 * 写锁的maxLeases是1，所以说如果你要加一个写锁成功的话，你必须是在/locks/lock_01目录里，是处于第一个位置的，index = 0，才能小于maxLeases，写锁才能够加成功
 * 但是此时children中，第一个的是别人加的读锁，所以此时你的写锁一定是失败的。index = 1 < maxLeases = 1，条件是不成立的
 * 他会给他的前一个节点加一个监听器，如果前面一个节点释放了锁，他就会被唤醒，再次尝试判断，他是不是处于处于当前这个children列表中的第一个，如果是第一个的话，才能是加写锁成功
 * 除非是前面加的读锁先释放掉了，写锁才能被唤醒然后尝试加写锁成功
 *
 * （3）写锁+读锁
 * [_c_39258b5d-4383-466e-9b86-fda522ea061a-__WRIT__0000000006, _c_d4842035-5ba2-488f-93a4-f85fafd5cc32-__READ__0000000007]
 * 如果是同一个客户端，先加写锁，再加读锁，是可以成功的，同一个客户端的写锁+读锁是不互斥的
 *
 * 如果是不同的客户端先加写锁，再加读锁呢？
 *             if ( node.contains(WRITE_LOCK_NAME) )
 *             {
 *                 firstWriteIndex = Math.min(index, firstWriteIndex);
 *             }
 * 遍历children列表，如果在children列表中发现了一个写锁，此时会干什么呢？
 * firstWriteIndex = 0
 * ourIndex = 1
 * boolean getTheLock = ourIndex（1） < firstWriteIndex（0）
 * 这样的话呢，只要你是先加了一个写锁，写锁之后要加读锁的话，此时都会一律不让加读锁，会卡住，加锁就失败了，wait等待
 * 不同的客户端，如果先加写锁，再加读锁，一定是互斥的
 * 只有等到前面的写锁释放掉了，这个读锁他才能加锁成功，后面一大堆的人跟着加了N个读锁，也是一样的排队等待
 *
 * （4）写锁+写锁
 * 如果有一个人先加了写锁，然后后面又有一个人来加了这个写锁，此时会发现第二个写锁的node是第二位，不是第一位，所以会导致写锁也会等待，加锁失败，只有第一个写锁先成功了，第二个写锁才能成功
 */