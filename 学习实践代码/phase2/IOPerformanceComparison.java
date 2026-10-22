package com.example.practice.phase2;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 性能对比测试：传统IO vs mmap vs DirectBuffer
 */
public class IOPerformanceComparison {
    
    private static final int FILE_SIZE = 100 * 1024 * 1024;  // 100MB
    private static final int ITERATIONS = 50;
    
    public static void main(String[] args) throws Exception {
        System.out.println("=" + "=".repeat(70));
        System.out.println("IO性能对比测试");
        System.out.println("=" + "=".repeat(70));
        System.out.println("文件大小: " + (FILE_SIZE / 1024 / 1024) + " MB");
        System.out.println("迭代次数: " + ITERATIONS);
        System.out.println();
        
        // 预热JVM
        System.out.println("🔥 预热JVM...");
        warmUp();
        System.out.println("✅ 预热完成\n");
        
        // 测试1：HeapBuffer + RandomAccessFile
        long time1 = testHeapBuffer();
        
        // 测试2：DirectBuffer + FileChannel
        long time2 = testDirectBuffer();
        
        // 测试3：MappedByteBuffer
        long time3 = testMmap();
        
        // 输出对比结果
        printComparison(time1, time2, time3);
    }
    
    /**
     * 预热JVM
     */
    private static void warmUp() throws Exception {
        for (int i = 0; i < 5; i++) {
            File file = new File("/tmp/warmup.dat");
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
                 FileChannel channel = raf.getChannel()) {
                MappedByteBuffer buffer = channel.map(
                    FileChannel.MapMode.READ_WRITE, 0, 1024 * 1024);
                buffer.putInt(42);
            }
            file.delete();
        }
    }
    
    /**
     * 测试1：Heap Buffer + RandomAccessFile
     */
    private static long testHeapBuffer() throws Exception {
        System.out.println("📝 测试1：Heap Buffer + RandomAccessFile");
        File file = new File("/tmp/heap_test.dat");
        byte[] data = new byte[4096];
        
        long start = System.currentTimeMillis();
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            for (int i = 0; i < ITERATIONS; i++) {
                raf.seek(0);
                for (int j = 0; j < FILE_SIZE / 4096; j++) {
                    raf.write(data);
                }
            }
        }
        
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  耗时: " + elapsed + " ms");
        System.out.println("  吞吐量: " + 
            (FILE_SIZE * ITERATIONS / 1024 / 1024 / (elapsed / 1000.0)) + " MB/s\n");
        
        file.delete();
        return elapsed;
    }
    
    /**
     * 测试2：Direct Buffer + FileChannel
     */
    private static long testDirectBuffer() throws Exception {
        System.out.println("📝 测试2：Direct Buffer + FileChannel");
        File file = new File("/tmp/direct_test.dat");
        ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
        
        long start = System.currentTimeMillis();
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel()) {
            
            for (int i = 0; i < ITERATIONS; i++) {
                channel.position(0);
                for (int j = 0; j < FILE_SIZE / 4096; j++) {
                    buffer.clear();
                    channel.write(buffer);
                }
            }
        }
        
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  耗时: " + elapsed + " ms");
        System.out.println("  吞吐量: " + 
            (FILE_SIZE * ITERATIONS / 1024 / 1024 / (elapsed / 1000.0)) + " MB/s\n");
        
        file.delete();
        return elapsed;
    }
    
    /**
     * 测试3：MappedByteBuffer (mmap)
     */
    private static long testMmap() throws Exception {
        System.out.println("📝 测试3：MappedByteBuffer (mmap)");
        File file = new File("/tmp/mmap_test.dat");
        
        long start = System.currentTimeMillis();
        
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel()) {
            
            MappedByteBuffer buffer = channel.map(
                FileChannel.MapMode.READ_WRITE, 0, FILE_SIZE);
            
            byte[] data = new byte[4096];
            for (int i = 0; i < ITERATIONS; i++) {
                buffer.position(0);
                for (int j = 0; j < FILE_SIZE / 4096; j++) {
                    buffer.put(data);
                }
            }
        }
        
        long elapsed = System.currentTimeMillis() - start;
        System.out.println("  耗时: " + elapsed + " ms");
        System.out.println("  吞吐量: " + 
            (FILE_SIZE * ITERATIONS / 1024 / 1024 / (elapsed / 1000.0)) + " MB/s\n");
        
        file.delete();
        return elapsed;
    }
    
    /**
     * 打印对比结果
     */
    private static void printComparison(long heap, long direct, long mmap) {
        System.out.println("=" + "=".repeat(70));
        System.out.println("性能对比总结");
        System.out.println("=" + "=".repeat(70));
        
        long baseline = heap;
        
        System.out.printf("%-30s %10s %15s %15s%n", 
            "方法", "耗时(ms)", "相对性能", "吞吐量提升");
        System.out.println("-".repeat(72));
        
        System.out.printf("%-30s %10d %15s %15s%n", 
            "Heap Buffer", heap, "100%", "-");
        
        System.out.printf("%-30s %10d %15.1f%% %15.1f%%%n", 
            "Direct Buffer", direct, 
            (baseline * 100.0 / direct), 
            ((heap - direct) * 100.0 / heap));
        
        System.out.printf("%-30s %10d %15.1f%% %15.1f%%%n", 
            "mmap", mmap, 
            (baseline * 100.0 / mmap), 
            ((heap - mmap) * 100.0 / heap));
        
        System.out.println("=" + "=".repeat(70));
        
        // 推荐
        long best = Math.min(Math.min(heap, direct), mmap);
        String recommendation;
        if (best == mmap) {
            recommendation = "✅ 推荐使用 mmap，性能最佳！";
        } else if (best == direct) {
            recommendation = "✅ 推荐使用 DirectBuffer，性能较好！";
        } else {
            recommendation = "⚠️  Heap Buffer 性能最差，不推荐！";
        }
        
        System.out.println("\n" + recommendation);
    }
}

