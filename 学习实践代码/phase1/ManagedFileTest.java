package com.example.practice.phase1;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ManagedFile 测试用例
 * 
 * 测试场景：
 * 1. 正常使用和释放
 * 2. 多线程并发访问
 * 3. 优雅关闭
 * 4. 超时强制关闭
 */
public class ManagedFileTest {
    
    public static void main(String[] args) throws Exception {
        // 测试1：正常使用和释放
        testNormalUsage();
        
        // 测试2：多线程并发访问
        testConcurrentAccess();
        
        // 测试3：优雅关闭
        testGracefulShutdown();
        
        // 测试4：强制关闭
        testForcedShutdown();
    }
    
    /**
     * 测试1：正常使用和释放
     */
    private static void testNormalUsage() throws IOException {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("测试1：正常使用和释放");
        System.out.println("=".repeat(60));
        
        ManagedFile file = new ManagedFile("/tmp/test1.txt");
        System.out.println("📊 初始引用计数: " + file.getRefCount());
        
        // 写入数据
        file.writeData("Hello World from Test 1\n");
        System.out.println("📝 数据写入完成");
        
        // 读取数据验证
        String content = file.readData(100);
        System.out.println("📖 读取内容: " + content.trim());
        
        // 主动释放初始引用
        file.release();
        System.out.println("📊 释放后引用计数: " + file.getRefCount());
        System.out.println("📊 清理完成: " + file.isCleanupOver());
    }
    
    /**
     * 测试2：多线程并发访问
     * 
     * 关键学习点：
     * - 引用计数如何保证多线程安全
     * - 每个线程使用资源时都需要 hold/release
     */
    private static void testConcurrentAccess() throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("测试2：多线程并发访问");
        System.out.println("=".repeat(60));
        
        ManagedFile file = new ManagedFile("/tmp/test2.txt");
        ExecutorService executor = Executors.newFixedThreadPool(5);
        CountDownLatch latch = new CountDownLatch(5);
        
        System.out.println("📊 启动5个线程并发写入...");
        
        // 5个线程并发写入
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 10; j++) {
                        file.writeData("Thread-" + threadId + ": Message " + j + "\n");
                    }
                    System.out.println("  ✅ Thread-" + threadId + " 写入完成");
                } catch (IOException e) {
                    System.err.println("  ❌ Thread-" + threadId + " 写入失败: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        latch.await();
        System.out.println("📊 所有线程写入完成，当前引用计数: " + file.getRefCount());
        
        // 读取验证
        String content = file.readData(1000);
        int lineCount = content.split("\n").length;
        System.out.println("📖 文件共写入 " + lineCount + " 行数据");
        
        file.release();
        executor.shutdown();
    }
    
    /**
     * 测试3：优雅关闭
     * 
     * 关键学习点：
     * - shutdown 如何等待现有引用释放
     * - 如何拒绝新的 hold 请求
     */
    private static void testGracefulShutdown() throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("测试3：优雅关闭");
        System.out.println("=".repeat(60));
        
        ManagedFile file = new ManagedFile("/tmp/test3.txt");
        
        // 模拟有线程正在使用
        if (file.hold()) {
            System.out.println("📌 线程1持有引用，当前引用计数: " + file.getRefCount());
            
            // 另一个线程尝试关闭
            new Thread(() -> {
                System.out.println("🔒 线程2调用 shutdown (5秒超时)...");
                file.shutdown(5000);
                System.out.println("🔒 shutdown 调用完成");
            }).start();
            
            Thread.sleep(1000);  // 模拟使用1秒
            
            System.out.println("📌 线程1释放引用");
            file.release();  // 业务线程释放
        }
        
        Thread.sleep(100);
        file.release();  // 释放初始引用
        
        System.out.println("📊 最终状态:");
        System.out.println("  - 引用计数: " + file.getRefCount());
        System.out.println("  - 清理完成: " + file.isCleanupOver());
        System.out.println("  - 可用状态: " + file.isAvailable());
    }
    
    /**
     * 测试4：强制关闭（超时）
     * 
     * 关键学习点：
     * - 当引用未能正常释放时，如何强制清理
     * - 防止资源泄露的兜底机制
     */
    private static void testForcedShutdown() throws Exception {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("测试4：强制关闭（模拟资源泄露）");
        System.out.println("=".repeat(60));
        
        ManagedFile file = new ManagedFile("/tmp/test4.txt");
        
        // 持有引用但不释放（模拟泄露）
        if (file.hold()) {
            System.out.println("⚠️  持有引用但不释放（模拟泄露），引用计数: " + file.getRefCount());
        }
        
        // 第一次 shutdown
        System.out.println("\n⏰ T=0ms: 第一次 shutdown (1秒超时)");
        file.shutdown(1000);
        System.out.println("  - 引用计数: " + file.getRefCount());
        System.out.println("  - 可用状态: " + file.isAvailable());
        
        Thread.sleep(500);
        System.out.println("\n⏰ T=500ms: 第二次 shutdown");
        file.shutdown(1000);
        System.out.println("  - 引用计数: " + file.getRefCount());
        System.out.println("  - 清理完成: " + file.isCleanupOver());
        
        Thread.sleep(600);  // 累计超过1秒
        System.out.println("\n⏰ T=1100ms: 第三次 shutdown（触发强制关闭）");
        file.shutdown(1000);
        System.out.println("  - 引用计数: " + file.getRefCount() + " (负数表示强制关闭)");
        
        Thread.sleep(100);
        System.out.println("\n📊 最终状态:");
        System.out.println("  - 清理完成: " + file.isCleanupOver());
        System.out.println("  - 可用状态: " + file.isAvailable());
    }
}

