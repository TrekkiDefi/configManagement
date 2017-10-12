package com.fnpac.config.consumer;

/**
 * Created by 刘春龙 on 2017/10/12.
 */
public class ConsumerMain {

    public static void main(String[] args) throws InterruptedException {
        ServiceConsumer sc = new ServiceConsumer("localhost:2181");
        sc.getServices("configManagement");
        sc.printService();

        for (int i = 0; i < 10; i++) {
            sc.accessService("configManagement", "/web/index");
            Thread.sleep(10000);
        }
        Thread.sleep(1000000);
    }
}
