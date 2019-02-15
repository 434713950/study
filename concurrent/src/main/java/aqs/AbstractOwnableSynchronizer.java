package aqs;

import java.io.Serializable;

/**
 * <p></p>
 *
 * @author PengCheng
 * @date 2019/1/18
 */
public abstract class AbstractOwnableSynchronizer implements Serializable {


    /**
     * 持有排他锁的线程
     */
    private transient Thread exclusiveOwnerThread;

    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }

    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }
}
