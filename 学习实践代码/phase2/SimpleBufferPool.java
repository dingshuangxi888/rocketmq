package com.example.practice.phase2;

import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 实践任务：实现简化版的TransientStorePool
 * 
 * 学习要点：
 * 1. 对象池设计模式
 * 2. DirectBuffer的创建和复用
 * 3. 资源管理和监控
 */
public class SimpleBufferPool {
    private final int poolSize;
    private final int bufferSize;
    private final ConcurrentLinkedDeque<ByteBuffer> availableBuffers;
    private volatile long totalBorrowed = 0;
    private volatile long totalReturned = 0;
    
    public SimpleBufferPool(int poolSize, int bufferSize) {
        this.poolSize = poolSize;
        this.bufferSize = bufferSize;
        this.availableBuffers = new ConcurrentLinkedDeque<>();
    }
    
    /**
     * 初始化池：预分配所有DirectBuffer
     */
    public void init() {
        long start = System.currentTimeMillis();
        System.out.println("🔧 初始化Buffer池...");
        System.out.println("  - 池大小: " + poolSize);
        System.out.println("  - Buffer大小: " + (bufferSize / 1024 / 1024) + " MB");
        
        for (int i = 0; i < poolSize; i++) {
            // 分配堆外内存
            ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
            availableBuffers.offer(buffer);
            System.out.println("  ✅ Buffer-" + i + " 已分配");
        }
        
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("✅ 初始化完成，耗时: " + elapsed + " ms");
        System.out.println("📊 总内存占用: " + 
            (poolSize * bufferSize / 1024 / 1024) + " MB\n");
    }
    
    /**
     * 借用Buffer
     */
    public ByteBuffer borrowBuffer() {
        ByteBuffer buffer = availableBuffers.pollFirst();
        
        if (buffer != null) {
            totalBorrowed++;
            
            // 监控：池容量告警
            int remaining = availableBuffers.size();
            if (remaining < poolSize * 0.3) {
                System.out.println("⚠️  警告：Buffer池剩余 " + remaining + 
                    "/" + poolSize + " (" + 
                    (remaining * 100 / poolSize) + "%)");
            }
        } else {
            System.err.println("❌ Buffer池已耗尽！");
        }
        
        return buffer;
    }
    
    /**
     * 归还Buffer
     */
    public void returnBuffer(ByteBuffer buffer) {
        if (buffer != null) {
            // 重置Buffer状态
            buffer.clear();
            
            // 归还到池首（LIFO策略，提高缓存命中率）
            availableBuffers.offerFirst(buffer);
            totalReturned++;
        }
    }
    
    /**
     * 获取统计信息
     */
    public void printStats() {
        System.out.println("\n📊 Buffer池统计:");
        System.out.println("  - 总借用次数: " + totalBorrowed);
        System.out.println("  - 总归还次数: " + totalReturned);
        System.out.println("  - 当前可用数: " + availableBuffers.size() + "/" + poolSize);
        System.out.println("  - 使用中数量: " + (poolSize - availableBuffers.size()));
    }
    
    /**
     * 销毁池
     */
    public void destroy() {
        System.out.println("\n🧹 销毁Buffer池...");
        availableBuffers.clear();
        System.out.println("✅ 销毁完成");
    }
}

