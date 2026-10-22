package com.example.practice.phase1;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 实践任务：使用 ReferenceResource 管理文件资源
 * 
 * 学习要点：
 * 1. 继承 ReferenceResource 实现资源管理
 * 2. 正确实现 cleanup 方法
 * 3. 使用 hold/release 保护资源访问
 */
public class ManagedFile extends ReferenceResource {
    private final String filePath;
    private RandomAccessFile randomAccessFile;
    private FileChannel fileChannel;
    
    public ManagedFile(String filePath) throws IOException {
        this.filePath = filePath;
        this.randomAccessFile = new RandomAccessFile(filePath, "rw");
        this.fileChannel = randomAccessFile.getChannel();
        System.out.println("✅ 文件打开: " + filePath);
    }
    
    /**
     * 获取文件通道（使用前必须先 hold）
     */
    public FileChannel getFileChannel() {
        return this.fileChannel;
    }
    
    /**
     * 写入数据（演示正确的使用模式）
     */
    public void writeData(String data) throws IOException {
        // 先 hold 获取引用
        if (this.hold()) {
            try {
                // 安全地使用资源
                fileChannel.write(ByteBuffer.wrap(data.getBytes()));
            } finally {
                // 必须在 finally 中 release
                this.release();
            }
        } else {
            throw new IOException("文件不可用或已关闭");
        }
    }
    
    /**
     * 读取数据
     */
    public String readData(int length) throws IOException {
        if (this.hold()) {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(length);
                fileChannel.position(0);  // 从头开始读
                int bytesRead = fileChannel.read(buffer);
                if (bytesRead > 0) {
                    return new String(buffer.array(), 0, bytesRead);
                }
                return "";
            } finally {
                this.release();
            }
        } else {
            throw new IOException("文件不可用或已关闭");
        }
    }
    
    /**
     * 实现清理逻辑
     * 这是 ReferenceResource 的核心扩展点
     */
    @Override
    protected boolean cleanup(long currentRef) {
        System.out.println("🧹 开始清理文件资源: " + filePath + ", refCount=" + currentRef);
        
        try {
            // 1. 关闭 FileChannel
            if (fileChannel != null && fileChannel.isOpen()) {
                fileChannel.close();
                System.out.println("  - FileChannel 已关闭");
            }
            
            // 2. 关闭 RandomAccessFile
            if (randomAccessFile != null) {
                randomAccessFile.close();
                System.out.println("  - RandomAccessFile 已关闭");
            }
            
            System.out.println("✅ 文件资源清理完成: " + filePath);
            return true;
            
        } catch (IOException e) {
            System.err.println("❌ 清理失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    public String getFilePath() {
        return filePath;
    }
}

