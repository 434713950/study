package aqs;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class ReentrantLock implements Lock, java.io.Serializable {
    private static final long serialVersionUID = 7373984872572414699L;

    private final Sync sync;

    public ReentrantLock() {
        this.sync = new NonfairSync();
    }

    public ReentrantLock(boolean fair) {
        this.sync = fair ? new FairSync() : new NonfairSync();
    }


    abstract static class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;

        abstract void lock();

        final Condition newCondition(){
            return new ConditionObject();
        }

        protected final boolean isHeldExclusively() {
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        /**
         * 尝试非公平取锁（即不参与排队，直接尝试获取）
         * @param acquires
         * @return
         */
        final boolean nonfairTryAcquire(int acquires /* 1 */) {
            //获取当前线程
            final Thread current = Thread.currentThread();
            //判断锁状态
            int c = getState();
            if (c == 0) {
                //占用排他锁
                if (compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (current == getExclusiveOwnerThread()) {
                //线程重入（当前抢锁的线程与占用排他锁线程一致）
                //状态+1
                int nextc = c + acquires;

                if (nextc < 0){
                    //锁状态判断
                    throw new Error("Maximum lock count exceeded");
                }
                setState(nextc);
                return true;
            }
            return false;
        }

        /**
         * 尝试释放
         * @param releases
         * @return
         */
        @Override
        protected final boolean tryRelease(int releases) {
            //锁的状态。（这里要考虑到重入锁之后state>=1）
            int c = getState() - releases;
            //当前线程不是排他锁占用线程无法执行
            if (Thread.currentThread() != getExclusiveOwnerThread()) {
                throw new IllegalMonitorStateException();
            }

            boolean free = false;
            //锁状态变为0,标志释放成功
            if (c == 0) {
                free = true;
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }


    }

    /**
     * 非公平锁
     */
    static final class NonfairSync extends Sync {

        @Override
        void lock() {
            //cas判断是否占有线程，公平锁和非公平锁的最大区别在于这里，当前线程是否直接获取锁，不进入等待队列
            if (compareAndSetState(0, 1)) {
                //设置排他锁占有标志
                setExclusiveOwnerThread(Thread.currentThread());
            } else {
                //排队等待锁释放
                acquire(1);
            }
        }

        @Override
        protected boolean tryAcquire(int acquires) {
            return nonfairTryAcquire(acquires);
        }
    }

    /**
     * 公平锁
     */
    static final class FairSync extends Sync {

        @Override
        void lock() {
            acquire(1);
        }


        @Override
        protected final boolean tryAcquire(int acquires) {
            final Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (!hasQueuedPredecessors() &&
                        compareAndSetState(0, acquires)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (current == getExclusiveOwnerThread()) {
                int nextc = c + acquires;
                if (nextc < 0) {
                    throw new Error("Maximum lock count exceeded");
                }
                setState(nextc);
                return true;
            }
            return false;
        }
    }

    @Override
    public void lock() {
        sync.lock();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {

    }

    @Override
    public boolean tryLock() {
        return sync.nonfairTryAcquire(1);
    }

    @Override
    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
        return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }

    @Override
    public void unlock() {
        sync.release(1);
    }

    @Override
    public Condition newCondition() {
        return sync.newCondition();
    }


}
