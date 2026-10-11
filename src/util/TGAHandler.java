package util;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Manejador de archivos TGA (Targa).
 * Soporta lectura y escritura de imágenes TGA con canal alpha.
 * 
 * El canal alpha en TGA se usa frecuentemente para almacenar información
 * adicional (como mapas de altura o máscaras), por lo que se preserva
 * cuidadosamente durante el procesamiento.
 * 
 * Formatos soportados:
 * - TGA sin comprimir de 24 bits (RGB)
 * - TGA sin comprimir de 32 bits (RGBA)
 * - TGA con compresión RLE de 24 y 32 bits
 * 
 * @author vmonge
 */
public final class TGAHandler {
    
    // ==================== CONSTANTES TGA ====================
    
    /** Tipo de imagen: sin datos */
    private static final int TGA_NO_IMAGE = 0;
    
    /** Tipo de imagen: mapa de colores sin comprimir */
    private static final int TGA_COLORMAPPED = 1;
    
    /** Tipo de imagen: color real sin comprimir */
    private static final int TGA_TRUECOLOR = 2;
    
    /** Tipo de imagen: escala de grises sin comprimir */
    private static final int TGA_GRAYSCALE = 3;
    
    /** Tipo de imagen: mapa de colores con RLE */
    private static final int TGA_COLORMAPPED_RLE = 9;
    
    /** Tipo de imagen: color real con RLE */
    private static final int TGA_TRUECOLOR_RLE = 10;
    
    /** Tipo de imagen: escala de grises con RLE */
    private static final int TGA_GRAYSCALE_RLE = 11;
    
    /** Tamaño del header TGA en bytes */
    private static final int TGA_HEADER_SIZE = 18;
    
    // ==================== CONSTRUCTOR PRIVADO ====================
    
    /**
     * Constructor privado - clase utilitaria.
     */
    private TGAHandler() {
        throw new UnsupportedOperationException("Utility class - do not instantiate");
    }
    
    // ==================== LECTURA DE TGA ====================
    
    /**
     * Lee un archivo TGA y lo convierte en BufferedImage.
     * Preserva el canal alpha si existe.
     * 
     * @param file Archivo TGA a leer
     * @return BufferedImage con la imagen cargada
     * @throws IOException Si hay error de lectura o formato no soportado
     */
    public static BufferedImage read(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            // Leer header
            byte[] header = new byte[TGA_HEADER_SIZE];
            if (fis.read(header) != TGA_HEADER_SIZE) {
                throw new IOException("Incomplete TGA header");
            }
            
            // Parsear header
            TGAHeader tgaHeader = parseHeader(header);
            
            // Saltar ID y mapa de colores si existen
            if (tgaHeader.idLength > 0) {
                fis.skip(tgaHeader.idLength);
            }
            if (tgaHeader.colorMapType == 1) {
                int colorMapSize = tgaHeader.colorMapLength * (tgaHeader.colorMapDepth / 8);
                fis.skip(colorMapSize);
            }
            
            // Leer datos de imagen
            int pixelDepth = tgaHeader.pixelDepth;
            int bytesPerPixel = pixelDepth / 8;
            int dataSize = tgaHeader.width * tgaHeader.height * bytesPerPixel;
            byte[] imageData = new byte[dataSize];
            
            // Verificar tipo de imagen
            boolean isRLE = (tgaHeader.imageType == TGA_TRUECOLOR_RLE || 
                           tgaHeader.imageType == TGA_GRAYSCALE_RLE ||
                           tgaHeader.imageType == TGA_COLORMAPPED_RLE);
            
            if (isRLE) {
                // Decodificar RLE
                imageData = decodeRLE(fis, tgaHeader.width, tgaHeader.height, bytesPerPixel);
            } else {
                // Leer datos sin comprimir
                int bytesRead = 0;
                while (bytesRead < dataSize) {
                    int read = fis.read(imageData, bytesRead, dataSize - bytesRead);
                    if (read == -1) break;
                    bytesRead += read;
                }
            }
            
            // Crear BufferedImage
            BufferedImage image = createBufferedImage(imageData, tgaHeader);
            
            return image;
        }
    }
    
    /**
     * Parsea el header TGA.
     * 
     * @param header Bytes del header
     * @return Estructura con los datos del header
     */
    private static TGAHeader parseHeader(byte[] header) {
        TGAHeader h = new TGAHeader();
        
        h.idLength = header[0] & 0xFF;
        h.colorMapType = header[1] & 0xFF;
        h.imageType = header[2] & 0xFF;
        
        // Color map specification (5 bytes)
        h.colorMapOrigin = readShort(header, 3);
        h.colorMapLength = readShort(header, 5);
        h.colorMapDepth = header[7] & 0xFF;
        
        // Image specification (10 bytes)
        h.xOrigin = readShort(header, 8);
        h.yOrigin = readShort(header, 10);
        h.width = readShort(header, 12);
        h.height = readShort(header, 14);
        h.pixelDepth = header[16] & 0xFF;
        h.imageDescriptor = header[17] & 0xFF;
        
        return h;
    }
    
    /**
     * Lee un short (2 bytes) en little-endian.
     */
    private static int readShort(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }
    
    /**
     * Decodifica datos comprimidos con RLE.
     * 
     * @param fis Stream de entrada
     * @param width Ancho de la imagen
     * @param height Alto de la imagen
     * @param bytesPerPixel Bytes por píxel
     * @return Datos decodificados
     * @throws IOException Si hay error de lectura
     */
    private static byte[] decodeRLE(FileInputStream fis, int width, int height, int bytesPerPixel) 
            throws IOException {
        int totalPixels = width * height;
        byte[] output = new byte[totalPixels * bytesPerPixel];
        int currentPixel = 0;
        byte[] pixel = new byte[bytesPerPixel];
        
        while (currentPixel < totalPixels) {
            int packetHeader = fis.read();
            if (packetHeader == -1) break;
            
            int packetType = (packetHeader & 0x80) >> 7;
            int packetCount = (packetHeader & 0x7F) + 1;
            
            if (packetType == 1) {
                // Run-length packet: un píxel repetido
                fis.read(pixel);
                for (int i = 0; i < packetCount && currentPixel < totalPixels; i++) {
                    System.arraycopy(pixel, 0, output, currentPixel * bytesPerPixel, bytesPerPixel);
                    currentPixel++;
                }
            } else {
                // Raw packet: píxeles sin comprimir
                for (int i = 0; i < packetCount && currentPixel < totalPixels; i++) {
                    fis.read(pixel);
                    System.arraycopy(pixel, 0, output, currentPixel * bytesPerPixel, bytesPerPixel);
                    currentPixel++;
                }
            }
        }
        
        return output;
    }
    
    /**
     * Crea un BufferedImage a partir de los datos TGA.
     * 
     * @param imageData Datos de píxeles
     * @param header Header TGA
     * @return BufferedImage creada
     */
    private static BufferedImage createBufferedImage(byte[] imageData, TGAHeader header) {
        int width = header.width;
        int height = header.height;
        int bytesPerPixel = header.pixelDepth / 8;
        
        // Determinar si tiene alpha
        boolean hasAlpha = (bytesPerPixel == 4);
        int imageType = hasAlpha ? BufferedImage.TYPE_4BYTE_ABGR : BufferedImage.TYPE_3BYTE_BGR;
        
        BufferedImage image = new BufferedImage(width, height, imageType);
        
        // Verificar orientación (bit 5 del descriptor: 0=bottom-up, 1=top-down)
        boolean topDown = (header.imageDescriptor & 0x20) != 0;
        
        // Copiar píxeles
        for (int y = 0; y < height; y++) {
            int destY = topDown ? y : (height - 1 - y);
            
            for (int x = 0; x < width; x++) {
                int srcOffset = (y * width + x) * bytesPerPixel;
                
                // TGA almacena en BGRA, convertir a ARGB
                int b = imageData[srcOffset] & 0xFF;
                int g = imageData[srcOffset + 1] & 0xFF;
                int r = imageData[srcOffset + 2] & 0xFF;
                int a = hasAlpha ? (imageData[srcOffset + 3] & 0xFF) : 255;
                
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                image.setRGB(x, destY, argb);
            }
        }
        
        return image;
    }
    
    // ==================== ESCRITURA DE TGA ====================
    
    /**
     * Escribe un BufferedImage como archivo TGA.
     * Preserva el canal alpha si la imagen lo tiene.
     * 
     * @param image Imagen a guardar
     * @param file Archivo de destino
     * @throws IOException Si hay error de escritura
     */
    public static void write(BufferedImage image, File file) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        boolean hasAlpha = image.getColorModel().hasAlpha();
        int bytesPerPixel = hasAlpha ? 4 : 3;
        int pixelDepth = bytesPerPixel * 8;
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            // Escribir header
            byte[] header = new byte[TGA_HEADER_SIZE];
            header[0] = 0; // ID length
            header[1] = 0; // Color map type
            header[2] = TGA_TRUECOLOR; // Image type (sin comprimir)
            // Color map specification (bytes 3-7) = 0
            // X origin (bytes 8-9) = 0
            // Y origin (bytes 10-11) = 0
            header[12] = (byte) (width & 0xFF);
            header[13] = (byte) ((width >> 8) & 0xFF);
            header[14] = (byte) (height & 0xFF);
            header[15] = (byte) ((height >> 8) & 0xFF);
            header[16] = (byte) pixelDepth;
            // Bit 5 = 1 para top-down, bits 0-3 = alpha bits
            header[17] = (byte) (0x20 | (hasAlpha ? 8 : 0));
            
            fos.write(header);
            
            // Escribir datos de píxeles en formato BGRA (o BGR)
            byte[] pixelData = new byte[bytesPerPixel];
            
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(x, y);
                    
                    // Extraer componentes ARGB
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    
                    // Escribir en orden BGRA
                    pixelData[0] = (byte) b;
                    pixelData[1] = (byte) g;
                    pixelData[2] = (byte) r;
                    if (hasAlpha) {
                        pixelData[3] = (byte) a;
                    }
                    
                    fos.write(pixelData);
                }
            }
            
            // Escribir footer TGA 2.0 (opcional pero recomendado)
            byte[] footer = new byte[26];
            // Extension offset = 0 (no extension)
            // Developer offset = 0 (no developer area)
            // Signature "TRUEVISION-XFILE."
            String signature = "TRUEVISION-XFILE.";
            System.arraycopy(signature.getBytes(), 0, footer, 8, signature.length());
            footer[25] = 0; // Null terminator
            fos.write(footer);
        }
    }
    
    // ==================== CLASE AUXILIAR PARA HEADER ====================
    
    /**
     * Estructura para almacenar los datos del header TGA.
     */
    private static class TGAHeader {
        int idLength;
        int colorMapType;
        int imageType;
        int colorMapOrigin;
        int colorMapLength;
        int colorMapDepth;
        int xOrigin;
        int yOrigin;
        int width;
        int height;
        int pixelDepth;
        int imageDescriptor;
    }
    
    // ==================== MÉTODOS DE UTILIDAD ====================
    
    /**
     * Verifica si un archivo es un TGA válido.
     * 
     * @param file Archivo a verificar
     * @return true si parece ser un archivo TGA válido
     */
    public static boolean isValidTGA(File file) {
        if (!file.exists() || !file.isFile()) {
            return false;
        }
        
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[TGA_HEADER_SIZE];
            if (fis.read(header) != TGA_HEADER_SIZE) {
                return false;
            }
            
            int imageType = header[2] & 0xFF;
            int pixelDepth = header[16] & 0xFF;
            
            // Verificar tipo de imagen válido
            boolean validType = (imageType == TGA_TRUECOLOR || 
                               imageType == TGA_TRUECOLOR_RLE ||
                               imageType == TGA_GRAYSCALE ||
                               imageType == TGA_GRAYSCALE_RLE);
            
            // Verificar profundidad válida
            boolean validDepth = (pixelDepth == 8 || pixelDepth == 16 || 
                                pixelDepth == 24 || pixelDepth == 32);
            
            return validType && validDepth;
            
        } catch (IOException e) {
            return false;
        }
    }
}
