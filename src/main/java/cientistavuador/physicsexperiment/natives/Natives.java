/*
 * This is free and unencumbered software released into the public domain.
 *
 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.
 *
 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 *
 * For more information, please refer to <https://unlicense.org>
 */
package cientistavuador.physicsexperiment.natives;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 *
 * @author Cien
 */
public class Natives {

    public static final String TEMP_FOLDER_NAME = "059c08b8-5031-4ec5-bcca-05b96127c7ac";
    public static final String NATIVES_VERSION = "version0";
    
    public static Path extract(String zipFile, String folderName) throws IOException {
        return new Natives(zipFile, folderName).extract();
    }

    private final ZipInputStream zipInput;
    private final Path folderPath;
    
    private String currentFileName;
    private Path currentFilePath;

    private Natives(String zipFile, String folderName) throws IOException {
        this.zipInput = new ZipInputStream(
                Natives.class.getResourceAsStream(zipFile),
                StandardCharsets.UTF_8
        );
        
        this.folderPath = Paths.get(
                System.getProperty("java.io.tmpdir"),
                TEMP_FOLDER_NAME,
                NATIVES_VERSION,
                folderName
        );
        Files.createDirectories(this.folderPath);
    }
    
    private void loadEntry(ZipEntry entry) {
        this.currentFileName = entry.getName();
        this.currentFilePath = this.folderPath.resolve(this.currentFileName);
    }
    
    private void writeFile() throws IOException {
        try (OutputStream out = Files.newOutputStream(this.currentFilePath)) {
            byte[] buffer = new byte[8192];
            int r;
            
            while ((r = this.zipInput.read(buffer)) != -1) {
                out.write(buffer, 0, r);
            }
        }
    }
    
    public Path extract() throws IOException {
        try (this.zipInput) {
            ZipEntry entry;
            while ((entry = this.zipInput.getNextEntry()) != null) {
                loadEntry(entry);
                writeFile();
            }
        }
        return this.folderPath;
    }
}
