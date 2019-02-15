/**
 * <p></p>
 *
 * @author PengCheng
 * @date 2019/2/14
 */
public class Test {

    public static volatile boolean stop = false;

    public static void main(String[] args) throws InterruptedException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stop){
                    System.out.println("1");
                }
            }
        }).start();

        Thread.sleep(200);
        stop = true;
    }
}
