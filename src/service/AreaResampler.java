package service;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.util.Arrays;
import util.AlphaMode;

/**
 * Implementación del algoritmo Area Resampling (Pixel Area Relation) para
 * reducción de imágenes. Cada píxel se trata como un área 1×1 y se calcula
 * la intersección exacta con los píxeles fuente.
 *
 * Usa alfa premultiplicado para mezclar correctamente píxeles con diferente
 * nivel de transparencia, evitando halos oscuros en bordes semitransparentes.
 *
 * Soporta dos modos de procesamiento del canal alfa:
 * - {@link AlphaMode#BINARY}: umbral binario (≥ 0.5 → opaco, &lt; 0.5 → transparente),
 *   ideal para texturas con transparencia de recorte.
 * - {@link AlphaMode#CONTINUOUS}: preserva el valor real del alfa, ideal para
 *   texturas con opacidad parcial (hielo, agua, cristal).
 *
 * Detalles de implementación:
 * - El filtro de caja es separable: los pesos de cada eje se precalculan una
 *   sola vez y cada fila fuente se reduce horizontalmente antes de acumularse
 *   en vertical.
 * - Los pesos son enteros exactos (solapes medidos en unidades de
 *   1 / (ancho fuente × ancho destino)) y las sumas se hacen con {@code long},
 *   por lo que el redondeo y el umbral de alfa no dependen de errores de coma
 *   flotante.
 *
 * @author vmonge
 */
public final class AreaResampler {

    private AreaResampler() {
        throw new UnsupportedOperationException("Clase utilitaria - no instanciar");
    }

    /**
     * Redimensiona una imagen usando area resampling con alfa premultiplicado.
     * Usa alfa binario por defecto (comportamiento original).
     *
     * @param source   Imagen fuente
     * @param dstWidth Ancho destino (debe ser &gt; 0)
     * @param dstHeight Alto destino (debe ser &gt; 0)
     * @return Nueva BufferedImage en TYPE_INT_ARGB
     */
    public static BufferedImage resize(BufferedImage source, int dstWidth, int dstHeight) {
        return resize(source, dstWidth, dstHeight, AlphaMode.BINARY);
    }

    /**
     * Redimensiona una imagen usando area resampling con alfa premultiplicado.
     *
     * @param source    Imagen fuente
     * @param dstWidth  Ancho destino (debe ser &gt; 0)
     * @param dstHeight Alto destino (debe ser &gt; 0)
     * @param alphaMode Modo de procesamiento del canal alfa
     * @return Nueva BufferedImage en TYPE_INT_ARGB
     */
    public static BufferedImage resize(BufferedImage source, int dstWidth, int dstHeight,
            AlphaMode alphaMode) {
        return resample(source, dstWidth, dstHeight, true, alphaMode);
    }

    /**
     * Redimensiona tratando RGB y Alpha como canales independientes.
     * Diseñado para TGA donde el alfa es una máscara de recorte y el RGB
     * contiene información válida en toda la imagen (foreground + background).
     *
     * <ul>
     *   <li>RGB: promedio ponderado por área sin premultiplicar por alfa</li>
     *   <li>Alpha: promedio ponderado por área con umbral binario (≥ 0.5 → 255)</li>
     * </ul>
     *
     * @param source    Imagen fuente
     * @param dstWidth  Ancho destino (debe ser &gt; 0)
     * @param dstHeight Alto destino (debe ser &gt; 0)
     * @return Nueva BufferedImage en TYPE_INT_ARGB
     */
    public static BufferedImage resizeIndependentAlpha(BufferedImage source,
            int dstWidth, int dstHeight) {
        return resizeIndependentAlpha(source, dstWidth, dstHeight, AlphaMode.BINARY);
    }

    /**
     * Redimensiona tratando RGB y Alpha como canales independientes, con el
     * modo de alfa indicado. Con {@link AlphaMode#CONTINUOUS} los cuatro canales
     * se promedian por separado, que es lo correcto para mapas de datos
     * (MER/MERS, normales) cuyo alfa no representa transparencia.
     *
     * @param source    Imagen fuente
     * @param dstWidth  Ancho destino (debe ser &gt; 0)
     * @param dstHeight Alto destino (debe ser &gt; 0)
     * @param alphaMode Modo de procesamiento del canal alfa
     * @return Nueva BufferedImage en TYPE_INT_ARGB
     */
    public static BufferedImage resizeIndependentAlpha(BufferedImage source,
            int dstWidth, int dstHeight, AlphaMode alphaMode) {
        return resample(source, dstWidth, dstHeight, false, alphaMode);
    }

    // ==================== NÚCLEO ====================

    /**
     * Filtro de caja separable con pesos enteros exactos.
     *
     * Para cada píxel destino, con {@code w} = solape (entero) y canales en 0–255:
     * <ul>
     *   <li>sumA = Σ w·a</li>
     *   <li>sumC = Σ w·a·c (premultiplicado) o Σ w·c (independiente)</li>
     * </ul>
     * La suma de pesos de un píxel destino es siempre {@code srcWidth × srcHeight}.
     */
    private static BufferedImage resample(BufferedImage source, int dstWidth, int dstHeight,
            boolean premultiply, AlphaMode alphaMode) {
        if (source == null) {
            throw new IllegalArgumentException("La imagen fuente no puede ser null");
        }
        if (dstWidth < 1 || dstHeight < 1) {
            throw new IllegalArgumentException("Las dimensiones destino deben ser >= 1");
        }
        if (alphaMode == null) {
            throw new IllegalArgumentException("El modo de alfa no puede ser null");
        }

        int srcWidth = source.getWidth();
        int srcHeight = source.getHeight();

        int[] srcPixels = readArgb(source);

        AxisWeights xWeights = new AxisWeights(srcWidth, dstWidth);
        AxisWeights yWeights = new AxisWeights(srcHeight, dstHeight);

        long totalWeight = (long) srcWidth * srcHeight;
        double invTotalWeight = 1.0 / totalWeight;
        long alphaThreshold = 255L * totalWeight;
        boolean continuous = alphaMode == AlphaMode.CONTINUOUS;

        // Fila fuente reducida horizontalmente (se reutiliza si la siguiente
        // fila destino empieza en la misma fila fuente)
        long[] rowA = new long[dstWidth];
        long[] rowR = new long[dstWidth];
        long[] rowG = new long[dstWidth];
        long[] rowB = new long[dstWidth];
        int cachedRow = -1;

        long[] accA = new long[dstWidth];
        long[] accR = new long[dstWidth];
        long[] accG = new long[dstWidth];
        long[] accB = new long[dstWidth];

        int[] dstPixels = new int[dstWidth * dstHeight];

        for (int dy = 0; dy < dstHeight; dy++) {
            Arrays.fill(accA, 0L);
            Arrays.fill(accR, 0L);
            Arrays.fill(accG, 0L);
            Arrays.fill(accB, 0L);

            for (int k = yWeights.offsets[dy]; k < yWeights.offsets[dy + 1]; k++) {
                int sy = yWeights.indices[k];
                long wy = yWeights.weights[k];

                if (sy != cachedRow) {
                    reduceRow(srcPixels, sy * srcWidth, xWeights, premultiply,
                            rowA, rowR, rowG, rowB);
                    cachedRow = sy;
                }

                for (int dx = 0; dx < dstWidth; dx++) {
                    accA[dx] += wy * rowA[dx];
                    accR[dx] += wy * rowR[dx];
                    accG[dx] += wy * rowG[dx];
                    accB[dx] += wy * rowB[dx];
                }
            }

            int rowOffset = dy * dstWidth;
            for (int dx = 0; dx < dstWidth; dx++) {
                long sumA = accA[dx];
                boolean opaque = 2 * sumA >= alphaThreshold;
                int finalA, finalR, finalG, finalB;

                if (premultiply) {
                    if (sumA == 0 || (!continuous && !opaque)) {
                        finalA = 0;
                        finalR = 0;
                        finalG = 0;
                        finalB = 0;
                    } else {
                        double invSumA = 1.0 / sumA;
                        finalA = continuous ? divRound(sumA, totalWeight, invTotalWeight) : 255;
                        finalR = divRound(accR[dx], sumA, invSumA);
                        finalG = divRound(accG[dx], sumA, invSumA);
                        finalB = divRound(accB[dx], sumA, invSumA);
                    }
                } else {
                    finalA = continuous
                            ? divRound(sumA, totalWeight, invTotalWeight)
                            : (opaque ? 255 : 0);
                    finalR = divRound(accR[dx], totalWeight, invTotalWeight);
                    finalG = divRound(accG[dx], totalWeight, invTotalWeight);
                    finalB = divRound(accB[dx], totalWeight, invTotalWeight);
                }

                dstPixels[rowOffset + dx] =
                        (finalA << 24) | (finalR << 16) | (finalG << 8) | finalB;
            }
        }

        // En TYPE_INT_ARGB los data elements del raster son directamente ARGB
        BufferedImage dest = new BufferedImage(dstWidth, dstHeight, BufferedImage.TYPE_INT_ARGB);
        dest.getRaster().setDataElements(0, 0, dstWidth, dstHeight, dstPixels);
        return dest;
    }

    /**
     * Reduce horizontalmente una fila fuente a {@code dstWidth} columnas.
     */
    private static void reduceRow(int[] srcPixels, int rowStart, AxisWeights xWeights,
            boolean premultiply, long[] rowA, long[] rowR, long[] rowG, long[] rowB) {
        int[] offsets = xWeights.offsets;
        int[] indices = xWeights.indices;
        int[] weights = xWeights.weights;

        for (int dx = 0; dx < rowA.length; dx++) {
            long sumA = 0, sumR = 0, sumG = 0, sumB = 0;

            for (int k = offsets[dx]; k < offsets[dx + 1]; k++) {
                int argb = srcPixels[rowStart + indices[k]];
                int a = argb >>> 24;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (premultiply) {
                    r *= a;
                    g *= a;
                    b *= a;
                }

                long w = weights[k];
                sumA += w * a;
                sumR += w * r;
                sumG += w * g;
                sumB += w * b;
            }

            rowA[dx] = sumA;
            rowR[dx] = sumR;
            rowG[dx] = sumG;
            rowB[dx] = sumB;
        }
    }

    /**
     * Pesos de área de un eje, precalculados una sola vez.
     *
     * Las coordenadas se escalan por {@code srcSize × dstSize}: el píxel fuente
     * {@code s} ocupa [s·dstSize, (s+1)·dstSize) y el destino {@code d} ocupa
     * [d·srcSize, (d+1)·srcSize). Así cada solape es un entero exacto y los
     * pesos de un píxel destino suman siempre {@code srcSize}.
     *
     * Las fuentes del destino {@code d} son {@code indices[offsets[d] .. offsets[d+1])}.
     */
    private static final class AxisWeights {
        final int[] offsets;
        final int[] indices;
        final int[] weights;

        AxisWeights(int srcSize, int dstSize) {
            // Cada destino aporta como mucho un píxel fuente compartido con el siguiente
            int capacity = srcSize + dstSize;
            offsets = new int[dstSize + 1];
            int[] idx = new int[capacity];
            int[] wts = new int[capacity];
            int n = 0;

            for (int d = 0; d < dstSize; d++) {
                offsets[d] = n;
                long lo = (long) d * srcSize;
                long hi = lo + srcSize;
                int first = (int) (lo / dstSize);
                int last = (int) ((hi - 1) / dstSize);

                for (int s = first; s <= last; s++) {
                    long overlap = Math.min(hi, (long) (s + 1) * dstSize)
                            - Math.max(lo, (long) s * dstSize);
                    idx[n] = s;
                    wts[n] = (int) overlap;
                    n++;
                }
            }
            offsets[dstSize] = n;

            indices = idx;
            weights = wts;
        }
    }

    // ==================== LECTURA DE PÍXELES ====================

    /**
     * Lee la imagen como ARGB de 8 bits por canal.
     *
     * <ul>
     *   <li>Escala de grises (PNG gris o gris+alfa): se lee directamente del
     *       raster. {@link BufferedImage#getRGB} las trata como gris lineal y las
     *       convierte a sRGB, aclarándolas (un gris 128 se leería como 188).</li>
     *   <li>sRGB de 8 bits por componente (PNG RGB/RGBA de ImageIO y TGA de
     *       {@link util.TGAHandler}): lectura en bloque del raster, varias veces
     *       más rápida que {@code getRGB} y con el mismo resultado.</li>
     *   <li>Resto (paletas, 16 bits, etc.): {@code getRGB}.</li>
     * </ul>
     */
    private static int[] readArgb(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        ColorModel cm = image.getColorModel();

        if (cm instanceof ComponentColorModel && !cm.isAlphaPremultiplied()) {
            if (cm.getColorSpace().getType() == ColorSpace.TYPE_GRAY) {
                return readGray(image.getRaster(), cm, width, height);
            }
            if (isByteSrgb(cm, image.getRaster())) {
                return readByteSrgb(image.getRaster(), cm.getNumComponents(), width, height);
            }
        }
        return image.getRGB(0, 0, width, height, null, 0, width);
    }

    private static boolean isByteSrgb(ColorModel cm, Raster raster) {
        if (!cm.getColorSpace().isCS_sRGB()
                || raster.getTransferType() != DataBuffer.TYPE_BYTE) {
            return false;
        }
        int components = cm.getNumComponents();
        if (components != (cm.hasAlpha() ? 4 : 3) || raster.getNumBands() != components) {
            return false;
        }
        for (int i = 0; i < components; i++) {
            if (cm.getComponentSize(i) != 8) {
                return false;
            }
        }
        return true;
    }

    /**
     * En un {@link ComponentColorModel} los data elements de cada píxel van en
     * orden de banda (R, G, B[, A]), sea cual sea su disposición en memoria.
     */
    private static int[] readByteSrgb(Raster raster, int components, int width, int height) {
        byte[] data = (byte[]) raster.getDataElements(0, 0, width, height, null);
        int[] argb = new int[width * height];
        for (int i = 0, j = 0; i < argb.length; i++, j += components) {
            int r = data[j] & 0xFF;
            int g = data[j + 1] & 0xFF;
            int b = data[j + 2] & 0xFF;
            int a = (components == 4) ? (data[j + 3] & 0xFF) : 255;
            argb[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return argb;
    }

    private static int[] readGray(Raster raster, ColorModel cm, int width, int height) {
        int[] gray = raster.getSamples(0, 0, width, height, 0, (int[]) null);
        int grayMax = (1 << cm.getComponentSize(0)) - 1;

        int[] alpha = null;
        int alphaMax = 255;
        if (cm.hasAlpha() && raster.getNumBands() > 1) {
            alpha = raster.getSamples(0, 0, width, height, 1, (int[]) null);
            alphaMax = (1 << cm.getComponentSize(1)) - 1;
        }

        int[] argb = new int[width * height];
        for (int i = 0; i < argb.length; i++) {
            int g = to8Bit(gray[i], grayMax);
            int a = (alpha != null) ? to8Bit(alpha[i], alphaMax) : 255;
            argb[i] = (a << 24) | (g << 16) | (g << 8) | g;
        }
        return argb;
    }

    private static int to8Bit(int value, int max) {
        return (max == 255) ? value : (int) ((value * 255L + max / 2) / max);
    }

    /**
     * Calcula {@code round(num / den)} exacto (mitades hacia arriba) para
     * {@code 0 ≤ num ≤ 255·den}.
     *
     * Estima el cociente con {@code invDen = 1.0 / den} y lo corrige con
     * aritmética entera: evita la división {@code long}, que es la operación
     * más costosa del bucle, sin perder exactitud en los empates.
     */
    private static int divRound(long num, long den, double invDen) {
        long q = (long) (num * invDen + 0.5);
        // q es correcto si -den ≤ 2·(num − q·den) < den
        long twiceRem = 2 * (num - q * den);
        if (twiceRem >= den) {
            q++;
        } else if (twiceRem < -den) {
            q--;
        }
        return (int) q;
    }
}
