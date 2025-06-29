package accounting.util;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * خدمة النسخ الاحتياطي والاستعادة المحسنة
 */
public class BackupService {
    
    private static final Logger LOGGER = Logger.getLogger(BackupService.class.getName());
    private static final String DATABASE_FILE = "agricultural_accounting.db";
    private static final String BACKUP_EXTENSION = ".backup";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    
    private final ImprovedDataManager dataManager;
    private final Path backupDirectory;
    
    public BackupService() {
        this.dataManager = ImprovedDataManager.getInstance();
        this.backupDirectory = Paths.get("backups");
        
        // إنشاء مجلد النسخ الاحتياطية إذا لم يكن موجوداً
        try {
            Files.createDirectories(backupDirectory);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "فشل في إنشاء مجلد النسخ الاحتياطية", e);
        }
    }
    
    /**
     * إنشاء نسخة احتياطية كاملة
     */
    public BackupResult createFullBackup(String description) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String backupFileName = "backup_" + timestamp + BACKUP_EXTENSION;
        Path backupPath = backupDirectory.resolve(backupFileName);
        
        try {
            // إنشاء ملف النسخة الاحتياطية المضغوط
            try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(backupPath))) {
                
                // إضافة ملف قاعدة البيانات
                addDatabaseToBackup(zipOut);
                
                // إضافة ملفات التكوين والإعدادات
                addConfigurationFiles(zipOut);
                
                // إضافة معلومات النسخة الاحتياطية
                addBackupMetadata(zipOut, description, timestamp);
            }
            
            long backupSize = Files.size(backupPath);
            
            LOGGER.info("تم إنشاء النسخة الاحتياطية بنجاح: " + backupFileName + 
                       " (الحجم: " + formatFileSize(backupSize) + ")");
            
            return new BackupResult(true, "تم إنشاء النسخة الاحتياطية بنجاح", 
                                  backupPath.toString(), backupSize);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "فشل في إنشاء النسخة الاحتياطية", e);
            
            // حذف الملف المعطوب إذا تم إنشاؤه
            try {
                Files.deleteIfExists(backupPath);
            } catch (IOException deleteEx) {
                LOGGER.log(Level.WARNING, "فشل في حذف ملف النسخة الاحتياطية المعطوب", deleteEx);
            }
            
            return new BackupResult(false, "فشل في إنشاء النسخة الاحتياطية: " + e.getMessage(), 
                                  null, 0);
        }
    }
    
    /**
     * استعادة نسخة احتياطية
     */
    public RestoreResult restoreBackup(String backupFilePath) {
        Path backupPath = Paths.get(backupFilePath);
        
        if (!Files.exists(backupPath)) {
            return new RestoreResult(false, "ملف النسخة الاحتياطية غير موجود", null);
        }
        
        try {
            // إنشاء نسخة احتياطية من الحالة الحالية قبل الاستعادة
            BackupResult currentBackup = createFullBackup("نسخة احتياطية قبل الاستعادة");
            
            // استخراج النسخة الاحتياطية
            try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(backupPath))) {
                
                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    
                    if (entry.getName().equals(DATABASE_FILE)) {
                        // استعادة قاعدة البيانات
                        restoreDatabaseFromBackup(zipIn);
                    } else if (entry.getName().startsWith("config/")) {
                        // استعادة ملفات التكوين
                        restoreConfigurationFile(zipIn, entry.getName());
                    }
                    
                    zipIn.closeEntry();
                }
            }
            
            LOGGER.info("تم استعادة النسخة الاحتياطية بنجاح من: " + backupFilePath);
            
            return new RestoreResult(true, "تم استعادة النسخة الاحتياطية بنجاح", 
                                   currentBackup.getBackupPath());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "فشل في استعادة النسخة الاحتياطية", e);
            return new RestoreResult(false, "فشل في استعادة النسخة الاحتياطية: " + e.getMessage(), 
                                   null);
        }
    }
    
    /**
     * الحصول على قائمة النسخ الاحتياطية المتاحة
     */
    public BackupInfo[] getAvailableBackups() {
        try {
            return Files.list(backupDirectory)
                    .filter(path -> path.toString().endsWith(BACKUP_EXTENSION))
                    .map(this::createBackupInfo)
                    .filter(info -> info != null)
                    .sorted((a, b) -> b.getCreationTime().compareTo(a.getCreationTime()))
                    .toArray(BackupInfo[]::new);
                    
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "فشل في قراءة مجلد النسخ الاحتياطية", e);
            return new BackupInfo[0];
        }
    }
    
    /**
     * حذف النسخ الاحتياطية القديمة
     */
    public int cleanupOldBackups(int keepCount) {
        BackupInfo[] backups = getAvailableBackups();
        int deletedCount = 0;
        
        if (backups.length > keepCount) {
            for (int i = keepCount; i < backups.length; i++) {
                try {
                    Files.delete(Paths.get(backups[i].getFilePath()));
                    deletedCount++;
                    LOGGER.info("تم حذف النسخة الاحتياطية القديمة: " + backups[i].getFileName());
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "فشل في حذف النسخة الاحتياطية: " + 
                              backups[i].getFileName(), e);
                }
            }
        }
        
        return deletedCount;
    }
    
    /**
     * التحقق من سلامة النسخة الاحتياطية
     */
    public ValidationResult validateBackup(String backupFilePath) {
        Path backupPath = Paths.get(backupFilePath);
        
        if (!Files.exists(backupPath)) {
            return new ValidationResult(false, "ملف النسخة الاحتياطية غير موجود");
        }
        
        try {
            boolean hasDatabaseFile = false;
            boolean hasMetadata = false;
            
            try (ZipInputStream zipIn = new ZipInputStream(Files.newInputStream(backupPath))) {
                ZipEntry entry;
                while ((entry = zipIn.getNextEntry()) != null) {
                    if (entry.getName().equals(DATABASE_FILE)) {
                        hasDatabaseFile = true;
                    } else if (entry.getName().equals("backup_metadata.txt")) {
                        hasMetadata = true;
                    }
                    zipIn.closeEntry();
                }
            }
            
            if (!hasDatabaseFile) {
                return new ValidationResult(false, "النسخة الاحتياطية لا تحتوي على ملف قاعدة البيانات");
            }
            
            if (!hasMetadata) {
                return new ValidationResult(false, "النسخة الاحتياطية لا تحتوي على معلومات التوقيت");
            }
            
            return new ValidationResult(true, "النسخة الاحتياطية سليمة");
            
        } catch (Exception e) {
            return new ValidationResult(false, "خطأ في قراءة النسخة الاحتياطية: " + e.getMessage());
        }
    }
    
    // الطرق المساعدة
    
    private void addDatabaseToBackup(ZipOutputStream zipOut) throws IOException {
        Path databasePath = Paths.get(DATABASE_FILE);
        if (Files.exists(databasePath)) {
            ZipEntry entry = new ZipEntry(DATABASE_FILE);
            zipOut.putNextEntry(entry);
            Files.copy(databasePath, zipOut);
            zipOut.closeEntry();
        }
    }
    
    private void addConfigurationFiles(ZipOutputStream zipOut) throws IOException {
        // إضافة ملفات التكوين إذا كانت موجودة
        Path configDir = Paths.get("config");
        if (Files.exists(configDir)) {
            Files.walk(configDir)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            String entryName = "config/" + configDir.relativize(file).toString();
                            ZipEntry entry = new ZipEntry(entryName);
                            zipOut.putNextEntry(entry);
                            Files.copy(file, zipOut);
                            zipOut.closeEntry();
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, "فشل في إضافة ملف التكوين: " + file, e);
                        }
                    });
        }
    }
    
    private void addBackupMetadata(ZipOutputStream zipOut, String description, String timestamp) throws IOException {
        ZipEntry entry = new ZipEntry("backup_metadata.txt");
        zipOut.putNextEntry(entry);
        
        String metadata = String.format(
            "Backup Created: %s%n" +
            "Description: %s%n" +
            "Application: Agricultural Accounting System%n" +
            "Version: 2.0%n",
            timestamp, description != null ? description : "نسخة احتياطية تلقائية"
        );
        
        zipOut.write(metadata.getBytes("UTF-8"));
        zipOut.closeEntry();
    }
    
    private void restoreDatabaseFromBackup(ZipInputStream zipIn) throws IOException {
        Path tempDatabasePath = Paths.get(DATABASE_FILE + ".temp");
        
        // استخراج قاعدة البيانات إلى ملف مؤقت
        Files.copy(zipIn, tempDatabasePath, StandardCopyOption.REPLACE_EXISTING);
        
        // إغلاق الاتصالات الحالية
        dataManager.shutdown();
        
        // استبدال قاعدة البيانات الحالية
        Path currentDatabasePath = Paths.get(DATABASE_FILE);
        Files.move(tempDatabasePath, currentDatabasePath, StandardCopyOption.REPLACE_EXISTING);
    }
    
    private void restoreConfigurationFile(ZipInputStream zipIn, String entryName) throws IOException {
        Path configPath = Paths.get(entryName);
        Files.createDirectories(configPath.getParent());
        Files.copy(zipIn, configPath, StandardCopyOption.REPLACE_EXISTING);
    }
    
    private BackupInfo createBackupInfo(Path backupPath) {
        try {
            String fileName = backupPath.getFileName().toString();
            long fileSize = Files.size(backupPath);
            LocalDateTime creationTime = LocalDateTime.ofInstant(
                Files.getLastModifiedTime(backupPath).toInstant(),
                java.time.ZoneId.systemDefault()
            );
            
            return new BackupInfo(fileName, backupPath.toString(), fileSize, creationTime);
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "فشل في قراءة معلومات النسخة الاحتياطية: " + backupPath, e);
            return null;
        }
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    // فئات النتائج
    
    public static class BackupResult {
        private final boolean success;
        private final String message;
        private final String backupPath;
        private final long backupSize;
        
        public BackupResult(boolean success, String message, String backupPath, long backupSize) {
            this.success = success;
            this.message = message;
            this.backupPath = backupPath;
            this.backupSize = backupSize;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getBackupPath() { return backupPath; }
        public long getBackupSize() { return backupSize; }
    }
    
    public static class RestoreResult {
        private final boolean success;
        private final String message;
        private final String previousBackupPath;
        
        public RestoreResult(boolean success, String message, String previousBackupPath) {
            this.success = success;
            this.message = message;
            this.previousBackupPath = previousBackupPath;
        }
        
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public String getPreviousBackupPath() { return previousBackupPath; }
    }
    
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
    }
    
    public static class BackupInfo {
        private final String fileName;
        private final String filePath;
        private final long fileSize;
        private final LocalDateTime creationTime;
        
        public BackupInfo(String fileName, String filePath, long fileSize, LocalDateTime creationTime) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.fileSize = fileSize;
            this.creationTime = creationTime;
        }
        
        public String getFileName() { return fileName; }
        public String getFilePath() { return filePath; }
        public long getFileSize() { return fileSize; }
        public LocalDateTime getCreationTime() { return creationTime; }
        
        public String getFormattedSize() {
            if (fileSize < 1024) return fileSize + " B";
            if (fileSize < 1024 * 1024) return String.format("%.1f KB", fileSize / 1024.0);
            if (fileSize < 1024 * 1024 * 1024) return String.format("%.1f MB", fileSize / (1024.0 * 1024));
            return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
        }
        
        public String getFormattedCreationTime() {
            return creationTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));
        }
    }
}

