package com.example.practice.phase1;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能对比测试：AtomicLong vs synchronized
 * 
 * 学习目标：
 * 1. 理解为什么 ReferenceResource 使用 AtomicLong
 * 2. 对比无锁和有锁的性能差异
 * 3. 理解在什么场景下应该选择哪种方式
 */
public class PerformanceTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("性能对比测试：AtomicLong vs synchronized");
        System.out.println("=".repeat(60));
        
        // 测试不同规模
        testWithIterations(1_000_000, "100万次操作");
        testWithIterations(10_000_000, "1000万次操作");
        testWithIterations(50_000_000, "5000万次操作");
        
        // 测试多线程场景
        System.out.println("\n多线程并发测试:");
        System.out.println("=".repeat(60));
        testMultiThread();
    }
    
    /**
     * 单线程性能测试
     */
    private static void testWithIterations(int iterations, String label) {
        System.out.println("\n📊 " + label);
        
        // 测试 AtomicLong
        AtomicLong atomicCounter = new AtomicLong(0);
        long start1 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            atomicCounter.incrementAndGet();
            atomicCounter.decrementAndGet();
        }
        long time1 = (System.nanoTime() - start1) / 1_000_000;  // 转换为毫秒
        
        // 测试 synchronized
        SyncCounter syncCounter = new SyncCounter();
        long start2 = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            syncCounter.increment();
            syncCounter.decrement();
        }
        long time2 = (System.nanoTime() - start2) / 1_000_000;
        
        // 输出结果
        System.out.println("  AtomicLong:       " + time1 + " ms");
        System.out.println("  synchronized:     " + time2 + " ms");
        System.out.println("  性能提升:         " + String.format("%.1f", (time2 * 100.0 / time1 - 100)) + "%");
        System.out.println("  速度倍数:         " + String.format("%.2f", time2 * 1.0 / time1) + "x");
    }
    
    /**
     * 多线程并发测试
     */
    private static void testMultiThread() throws InterruptedException {
        int threadCount = 10;
        int iterationsPerThread = 1_000_000;
        
        // 测试 AtomicLong
        AtomicLong atomicCounter = new AtomicLong(0);
        Thread[] atomicThreads = new Thread[threadCount];
        
        long start1 = System.nanoTime();
        for (int i = 0; i < threadCount; i++) {
            atomicThreads[i] = new Thread(() -> {
                for (int j = 0; j < iterationsPerThread; j++) {
                    atomicCounter.incrementAndGet();
                }
            });
            atomicThreads[i].start();
        }
        
        for (Thread thread : atomicThreads) {
            thread.join();
        }
        long time1 = (System.nanoTime() - start1) / 1_000_000;
        
        // 测试 synchronized
        SyncCounter syncCounter = new SyncCounter();
        Thread[] syncThreads = new Thread[threadCount];
        
        long start2 = System.nanoTime();
        for (int i = 0; i < threadCount; i++) {
            syncThreads[i] = new Thread(() -> {
                for (int j = 0; j < iterationsPerThread; j++) {
                    syncCounter.increment();
                }
            });
            syncThreads[i].start();
        }
        
        for (Thread thread : syncThreads) {
            thread.join();
        }
        long time2 = (System.nanoTime() - start2) / 1_000_000;
        
        // 输出结果
        System.out.println("\n📊 " + threadCount + "个线程，每线程" + (iterationsPerThread / 1_000_000) + "M次操作");
        System.out.println("  AtomicLong:       " + time1 + " ms (最终值: " + atomicCounter.get() + ")");
        System.out.println("  synchronized:     " + time2 + " ms (最终值: " + syncCounter.get() + ")");
        System.out.println("  性能提升:         " + String.format("%.1f", (time2 * 100.0 / time1 - 100)) + "%");
    }
    
    /**
     * 使用 synchronized 的计数器
     */
    static class SyncCounter {
        private long count = 0;
        
        public synchronized void increment() {
            count++;
        }
        
        public synchronized void decrement() {
            count--;
        }
        
        public synchronized long get() {
            return count;
        }
    }
}

