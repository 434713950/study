package aqs;

import sun.misc.Unsafe;

import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;

public abstract class AbstractQueuedSynchronizer
        extends AbstractOwnableSynchronizer
        implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static final long spinForTimeoutThreshold = 1000L;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("next"));

        } catch (Exception ex) { throw new Error(ex); }
    }

    protected AbstractQueuedSynchronizer() { }


    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
                acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
            selfInterrupt();
        }
    }

    /**
     * 直接获得锁
     * @param arg
     * @return
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 将排他的节点加入等待
     * @param mode
     * @return 返回当前线程包装的node
     */
    private Node addWaiter(Node mode) {
        //LIFO 后进先出，体现在这
        //将当前线程包装成node
        Node node = new Node(Thread.currentThread(), mode);

        // 将当前线程node加入队尾
        Node pred = tail;
        if (pred != null) {
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        //jvm相应操作
        enq(node);
        return node;
    }

    /**
     * jvm的objectMonitor
     * @param node
     * @return
     */
    private Node enq(final Node node) {
        for (;;) {
            //获取队尾node,如果没有代表队列未生成
            Node t = tail;
            if (t == null) {
                //初始化一个队列出来
                if (compareAndSetHead(new Node())) {
                    tail = head;
                }
            } else {
                //将当前node加入队尾
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     *  从队列中获取锁
     * @param node
     * @param arg
     * @return
     */
    final boolean acquireQueued(final Node node /*当前线程node*/, int arg /* 1 */) {
        boolean failed = true;
        try {
            //是否获取锁成功，可以执行了
            boolean interrupted = false;
            //自旋,
            for (;;) {
                //当前node的上一个node
                final Node p = node.predecessor();
                //判断当前线程节点的上一个节点为头节点的情况下，并尝试获取对当前线程获取锁
                //这里考虑到head将锁已经释放，但还在占用head位。可以直接让当前node占用锁，并将node设置为head
                if (p == head && tryAcquire(arg)) {
                    //获取锁成功
                    //当前线程成功拿到锁后设置当前node为head
                    setHead(node);
                    //将释放的节点关联清除，帮助GC
                    p.next = null;
                    failed = false;
                    return interrupted;
                }
                //清除队列中的cancel节点，同时清除当前线程标志并执行
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt()) {
                    interrupted = true;
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }

    /**
     * 去除队列中的cancel节点
     * @param pred
     * @param node
     * @return
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        //前一个节点的等待状态
        int ws = pred.waitStatus;
        //前一节点线程处于signal等待状态，直接放过
        if (ws == Node.SIGNAL) {
            return true;
        }

        //前一节点处于cancel状态，逐步移除cancel状态的节点
        if (ws > 0) {
            //当前节点前跳关联逐步前移一位，直到前一个节点等待状态为signal
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            //将节点关联起来形成新的队列顺序
            pred.next = node;
        } else {
            //如果前一个等待状态小于-1,则统一设置成-1
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }


    /**
     * 该方法的作用是将当前的节点从等待队列中清除
     *
     * 非常主要：
     *  唤醒线程条件：1.node处于队列头;2.node的等待状态>0;
     *
     * @param node
     */
    private void cancelAcquire(Node node) {
        if (node == null) {
            return;
        }

        //将当前节点线程清除
        node.thread = null;

        //这里是为了防止shouldParkAfterFailedAcquire方法实现失败
        Node pred = node.prev;
        while (pred.waitStatus > 0) {
            node.prev = pred = pred.prev;
        }

        //这里其实就是当前出入的node
        Node predNext = pred.next;
        //将节点等待状态设置cancelled的
        node.waitStatus = Node.CANCELLED;

        // 如果当前节点是队尾节点，则同步设置下一节点为null
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            int ws;
            //当前一个node不为head同时等待状态为signal
            if (pred != head &&
                    ((ws = pred.waitStatus) == Node.SIGNAL ||
                            (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                    pred.thread != null) {
                Node next = node.next;
                //下一个节点等待状态也为signal时
                if (next != null && next.waitStatus <= 0) {
                    //jvm objectMonitor同步设置
                    compareAndSetNext(pred, predNext, next);
                }
            } else {
                unparkSuccessor(node);
            }

            // help GC
            node.next = node;
        }
    }

    /**
     * 为节点下发执行许可
     * @param node
     */
    private void unparkSuccessor(Node node) {
       //如果当前节点等待状态为SIGNAL 则重置为0
        int ws = node.waitStatus;
        if (ws < 0) {
            compareAndSetWaitStatus(node, ws, 0);
        }

        Node s = node.next;
        //当前节点的下一个节点为null,或等待状态为cancel
        if (s == null || s.waitStatus > 0) {
            s = null;
            //取出当前节点到尾节点（包含）之间的第一个不为cancel的节点
            for (Node t = tail; t != null && t != node; t = t.prev) {
                if (t.waitStatus <= 0) {
                    s = t;
                }
            }
        }
        //下发执行许可
        if (s != null) {
            LockSupport.unpark(s.thread);
        }
    }

    /**
     * 消费当前线程的许可并让当前线程执行
     * @return
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        //清除中断，开始执行
        return Thread.interrupted();
    }


    public final boolean release(int arg) {
        //尝试对当前线程释放锁,一般而言当前线程处于队列head
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0) {
                unparkSuccessor(h);
            }
            return true;
        }
        return false;
    }

    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
        return tryAcquire(arg) ||
                doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 和doAcquire核心一致，只不过多了个时间条件
     * @param arg
     * @param nanosTimeout
     * @return
     * @throws InterruptedException
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L) {
            return false;
        }
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            //有时间条件的自旋
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    // help GC
                    p.next = null;
                    failed = false;
                    return true;
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L) {
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        nanosTimeout > spinForTimeoutThreshold) {
                    LockSupport.parkNanos(this, nanosTimeout);
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            }
        } finally {
            if (failed) {
                cancelAcquire(node);
            }
        }
    }


    /**
     * 当队列长度>1,判断head后的第一个节点是否是当前线程节点
     * @return
     */
    public final boolean hasQueuedPredecessors() {
        Node t = tail;
        Node h = head;
        Node s;
        //当队列长度>1,判断head后的第一个节点是否是当前线程节点
        return h != t &&
                ((s = h.next) == null || s.thread != Thread.currentThread());
    }

    static final class Node {
        static final Node SHARED = new Node();

        /**
         * 指示节点独占模式等待
         */
        static final Node EXCLUSIVE = null;

        /**
         * 接待你因为超时或者被打断而取消，将会从等待队列中移除，该节点持有的线程不会阻塞
         */
        static final int CANCELLED =  1;

        /**
         * 当前节点后继节点已经通过（或很快会）park阻塞，因此当当前节点释放或取消的时候必须unpark它的后继节点。
         * 为了避免竞争，acquire方法必须指示需要的signal信号，然后充实原子acquire方法，最后在失败的情况下阻塞
         * */
        static final int SIGNAL    = -1;

        /**
         * 节点当前处于等待队列中，直到状态在某个时间点被转化为0前它都不会作为同步队列的一个节点使用
         */
        static final int CONDITION = -2;

        /**
         * 共享模式下释放应当被传播到其它节点，这个状态会在doReleaseShared方法中设置（只有头节点会设置）来确保传播的持续，
         * 即使其它操作介入
         */
        static final int PROPAGATE = -3;

        volatile int waitStatus;

        volatile Node prev;

        volatile Node next;

        volatile Thread thread;

        Node nextWaiter;

        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * 获取队列的前一个线程node
         * @return
         * @throws NullPointerException
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null) {
                throw new NullPointerException();
            } else {
                return p;
            }
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /**
     * 队列的头节点
     */
    private transient volatile Node head;

    /**
     * 队列的头尾节点
     */
    private transient volatile Node tail;

    private volatile int state;

    /**
     * 获取锁状态
     * @return
     */
    protected final int getState() {
        return state;
    }

    /**
     * 设置锁状态
     * @param newState
     */
    protected final void setState(int newState) {
        state = newState;
    }

    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 中断当前线程
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }


    /**
     * condition 中有一个condition队列进行排队，该队列只针对CONDITION 和CANCEL两种waitStatus状态
     */
    public class ConditionObject implements Condition, Serializable {

        private transient Node firstWaiter;

        private transient Node lastWaiter;

        /**
         * 将当前线程加入到waiter队列中
         * @return
         */
        private Node addConditionWaiter() {
            Node t = lastWaiter;
            // If lastWaiter is cancelled, 清除.
            //waiter队列针对condition和cancel两种状态
            if (t != null && t.waitStatus != Node.CONDITION) {
                unlinkCancelledWaiters();
                t = lastWaiter;
            }
            Node node = new Node(Thread.currentThread(), Node.CONDITION);

            //最后一个waiter为空，说明在该waiter队列中不存在node.可以将当前线程node加入到首个waiter中
            if (t == null) {
                firstWaiter = node;
            } else {
                //否则的话，追尾添加当前线程节点
                t.nextWaiter = node;
            }
            //最后将lastWaiter设置成新加入的当前节点
            lastWaiter = node;
            return node;
        }

        /**
         * 清除队列中cancel的节点
         */
        private void unlinkCancelledWaiters() {
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null) {
                        firstWaiter = next;
                    } else {
                        trail.nextWaiter = next;
                    }
                    if (next == null) {
                        lastWaiter = trail;
                    }
                } else {
                    trail = t;
                }
                t = next;
            }
        }


        //当前线程节点需要经过自旋才成功加入等待队列，声明线程还未中断
        private static final int REINTERRUPT =  1;
        /** Mode meaning to throw InterruptedException on exit from wait */
        //当前线程节点直接加入队列成功
        private static final int THROW_IE    = -1;

        @Override
        public void await() throws InterruptedException {
            //在当前线程未被中断的情况情况下
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }

            Node node = addConditionWaiter();

            //如果线程占用锁中，则尝试释放锁
            int savedState = fullyRelease(node);
            int interruptMode = 0;

            //当前线程节点还未放入执行等待队列时
            while (!isOnSyncQueue(node)) {
                //消费当前线程许可，中断线程
                LockSupport.park(this);
                //将当前线程中断并加入等待队列
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
                    break;
                }
            }
            //如果从队列获取锁成功，并且当前线程interruptMode不为THROW_IE，需要变更interruptMode为REINTERRUPT
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE) {
                interruptMode = REINTERRUPT;
            }
            //当前节点的下个Condition
            if (node.nextWaiter != null){
                //清除状态为取消的waiter节点
                unlinkCancelledWaiters();
            }
            //THROW_IE REINTERRUPT
            if (interruptMode != 0) {
                reportInterruptAfterWait(interruptMode);
            }
        }


        /**
         * 释放锁
         * @param node 当前线程的节点（必须）
         * @return
         */
        final int fullyRelease(Node node) {
            boolean failed = true;
            try {
                //获取当前锁的状态
                int savedState = getState();

                //释放当前锁
                if (release(savedState)) {
                    failed = false;
                    return savedState;
                } else {
                    throw new IllegalMonitorStateException();
                }
            } finally {
                //释放失败，将该节点加入取消，等待移除出队列
                if (failed) {
                    node.waitStatus = Node.CANCELLED;
                }
            }
        }


        /**
         * 判断队列是否在同步队列中
         * @param node
         * @return
         */
        final boolean isOnSyncQueue(Node node) {
            if (node.waitStatus == Node.CONDITION || node.prev == null) {
                return false;
            }
            // If has successor, it must be on queue
            if (node.next != null){
                return true;
            }

            //从等待的同步队列中判断节点是否存在
            return findNodeFromTail(node);
        }

        /**
         * 从队列尾部向头部遍历查看当前节点是否在队列中
         * @param node
         * @return
         */
        private boolean findNodeFromTail(Node node) {
            Node t = tail;
            for (;;) {
                if (t == node) {
                    return true;
                }
                if (t == null) {
                    return false;
                }
                t = t.prev;
            }
        }

        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ?
                    (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                    0;
        }

        final boolean transferAfterCancelledWait(Node node) {
            //将当前线程节点（CONDITION）以状态0加入到等待队列
            if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
                enq(node);
                return true;
            }
            /*
             * 自旋判断节点是否加入等待队列中，没有加入则一直yield
             */
            while (!isOnSyncQueue(node)) {
                Thread.yield();
            }
            return false;
        }

        private void reportInterruptAfterWait(int interruptMode)
                throws InterruptedException {
            if (interruptMode == THROW_IE) {
                throw new InterruptedException();
            } else if (interruptMode == REINTERRUPT) {
                selfInterrupt();
            }
        }

        @Override
        public void awaitUninterruptibly() {

        }

        @Override
        public long awaitNanos(long nanosTimeout) throws InterruptedException {
            return 0;
        }

        @Override
        public boolean await(long time, TimeUnit unit) throws InterruptedException {
            return false;
        }

        @Override
        public boolean awaitUntil(Date deadline) throws InterruptedException {
            return false;
        }

        @Override
        public void signal() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }

            //去除第一个waiter Node
            Node first = firstWaiter;
            if (first != null) {
                doSignal(first);
            }
        }

        @Override
        public void signalAll() {
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            Node first = firstWaiter;
            if (first != null) {
                doSignalAll(first);
            }
        }

        /**
         * 判断当前线程是否持有排他锁
         * @return
         */
        protected boolean isHeldExclusively() {
            throw new UnsupportedOperationException();
        }

        /**
         * 按照waiter队列顺序唤醒单条
         * @param first
         */
        private void doSignal(Node first) {
            do {
                //这里是从waiter头部逐步移除整个队列
                if ( (firstWaiter = first.nextWaiter) == null) {
                    lastWaiter = null;
                }
                first.nextWaiter = null;
            } while (!transferForSignal(first) &&
                    (first = firstWaiter) != null);
        }

        /**
         * 将waiter队列中的node逐个加入到等待队列中并切换node waitstatus 为signal
         * 一次性唤醒
         * @param first
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }


        final boolean transferForSignal(Node node) {
            //如果节点匹配CONDITION状态，将其状态变更为0
            if (!compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
                return false;
            }

            //将当前线程节点加入到等待队列中
            Node p = enq(node);

            //如果当前线程节点的waitstatus 为取消状态或无法设置为signal
            int ws = p.waitStatus;
            if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL)) {
                //为节点线程加上许可
                LockSupport.unpark(node.thread);
            }
            return true;
        }
    }


    /**
     * 以下都是cas方法
     */

    protected final boolean compareAndSetState(int expect, int update) {
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                expect, update);
    }

    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }


}
