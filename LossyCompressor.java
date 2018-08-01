import javax.swing.*;
import java.awt.*;
import java.io.*;

public class LossyCompressor extends JFrame {
    private int height;
    private int width;
    private int[][] red; //red[i][j] is the value of red channel of the pixel[i][j]
    private int[][] green; //green[i][j] is the value of green channel of the pixel[i][j]
    private int[][] blue; //blue[i][j] is the value of blue channel of the pixel[i][j]
    private byte[] header;

    private int bytesToInt(byte[] bytes, int offset) {
        return ((int) bytes[offset] & 0xff) << 24 |
                ((int) bytes[offset - 1] & 0xff) << 16 |
                ((int) bytes[offset - 2] & 0xff) << 8 |
                ((int) bytes[offset - 3] & 0xff);
    }

    public void readBMP(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        boolean reversed; //whether the image is stored reversely -- bottom-up
        long emptyBytes; //the last bytes of a row may be meaningless
        header = new byte[54];

        bis.read(header, 0, 54); //The first 54 bytes are header
        width = bytesToInt(header, 21); //get the width of the image
        height = bytesToInt(header, 25); // get the height of the image
        red = new int[height][width];
        green = new int[height][width];
        blue = new int[height][width];

        //if the first bit of the 25th byte is 0, the image is reversed (bottom-up)
        reversed = ((int) header[25] & 0x80) == 0;

        if(width * 3 % 4 != 0) {
            emptyBytes = 4 - (width * 3 % 4);
        } else {
            emptyBytes = 0;
        }

        if(reversed) { //if the image is reversed, read from bottom to top
            for(int i = height - 1; i >= 0; i--) {
                for(int j = 0; j < width; j++) {
                    blue[i][j] = bis.read();
                    green[i][j] = bis.read();
                    red[i][j] = bis.read();
                }
                if(emptyBytes != 0) { //skip empty bytes
                    bis.skip(emptyBytes);
                }
            }
        } else {
            for(int i = 0; i < height; i++) {
                for(int j = 0; j < width; j++) {
                    blue[i][j] = bis.read();
                    green[i][j] = bis.read();
                    red[i][j] = bis.read();
                }
                if(emptyBytes != 0) {
                    bis.skip(emptyBytes);
                }
            }
        }

        fis.close();
        bis.close();
    }

    public void compress(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        BufferedOutputStream bos = new BufferedOutputStream(fos);
        YCbCr ycbcr;
        int ycbcr16;

        bos.write(header);

        for(int i = 0; i < height; i++) {
            for(int j = 0; j < width; j++) {
                if(i % 2 == 0 && j % 2 == 0) {
                    ycbcr = rgbToYCbCr(red[i][j], green[i][j], blue[i][j]); // convert the color space into yuv
                    // select lower 6 bits of Y channel, lower 5 bits of cb channel and lower 5 bits of cr channel
                    // combine these 16 bits, 2 bytes together and write into the compressed file
                    ycbcr16 = ((ycbcr.y >> 2) << 10) | ((ycbcr.cb >> 3) << 5) | (ycbcr.cr >> 3);
                    bos.write((ycbcr16 >> 8) & 0xFF);
                    bos.write(ycbcr16 & 0xFF);
                }
            }
        }

        bos.flush();

        fos.close();
        bos.close();
    }

    public void showBMP(String title, int x, int y) {
        this.setTitle(title);
        this.setSize(width, height);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLocation(x, y);

        this.setResizable(true);
        this.setVisible(true);

        DrawPanel drawPanel = new DrawPanel();

        this.add(drawPanel);
    }

    private YCbCr rgbToYCbCr(int r, int g, int b) { //rgb -> ycbcr
        int y = (int)(0.299 * r + 0.587 * g + 0.114 * b);
        int cb = (int)(-0.1687 * r - 0.3313 * g + 0.5 * b) + 128;
        int cr = (int)(0.5 * r - 0.4187 * g - 0.0813 * b) + 128;

        y = Integer.min(y, 255);
        cb = Integer.min(cb, 255);
        cr = Integer.min(cr, 255);

        y = Integer.max(y, 0);
        cb = Integer.max(cb, 0);
        cr = Integer.max(cr, 0);

        return new YCbCr(y, cb, cr);
    }

    public class DrawPanel extends JPanel {
        public void paint(Graphics g) {
            super.paint(g);
            for(int i = 0; i < height; i++) {
                for(int j = 0; j < width; j++) {
                    g.setColor(new Color(red[i][j], green[i][j], blue[i][j]));
                    g.fillRect(j, i, 1, 1);
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            FileSelector fs = new FileSelector();
            String path = fs.getPath();
            if(path != null && path.endsWith(".bmp")) {
                //the name of compressed IM3 file
                String compressedPath = path.substring(0, path.length() - 3) + "IM3";

                File originalFile = new File(path);
                File compressedFile = new File(compressedPath);

                LossyCompressor lc = new LossyCompressor();
                lc.readBMP(originalFile);
                lc.showBMP("Original", 100, 300); //show the image of the original file
                lc.compress(compressedFile);

                long originalSize = originalFile.length();
                long compressedSize = compressedFile.length();

                System.out.println("Original file size: " + originalSize);
                System.out.println("Compressed file size: " + compressedSize);
                System.out.println("Compression ratio: " + originalSize * 1.0 / compressedSize);

                //the name of decompressed file
                String decompressedPath = path.substring(0, path.length() - 4) + "_decompressed.bmp";
                File decompressedFile = new File(decompressedPath);
                IMFileReader fileReader = new IMFileReader();
                fileReader.readIM(compressedFile);
                fileReader.decompress(decompressedFile);

                lc.readBMP(decompressedFile);
                lc.showBMP("Compressed", 1000, 300); //show the image of compressed file
            } else if(path != null && path.endsWith(".IM3")) {
                File compressedFile = new File(path);
                LossyCompressor lc = new LossyCompressor();

                String decompressedPath = path.substring(0, path.length() - 4) + "_decompressed.bmp";
                File decompressedFile = new File(decompressedPath);
                IMFileReader fileReader = new IMFileReader();
                fileReader.readIM(compressedFile);
                fileReader.decompress(decompressedFile);

                lc.readBMP(decompressedFile);
                lc.showBMP("Compressed", 100, 300);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class IMFileReader {
    private int height;
    private int width;
    private int[][] y; //red[i][j] is the value of red channel of the pixel[i][j]
    private int[][] cb;
    private int[][] cr;
    private byte[] header;
    private boolean reversed; //whether the image is bottom-up
    private int emptyBytes; //the last bytes of a row may be meaningless

    private int bytesToInt(byte[] bytes, int offset) {
        return ((int) bytes[offset] & 0xff) << 24 |
                ((int) bytes[offset - 1] & 0xff) << 16 |
                ((int) bytes[offset - 2] & 0xff) << 8 |
                ((int) bytes[offset - 3] & 0xff);
    }

    public void readIM(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        BufferedInputStream bis = new BufferedInputStream(fis);
        header = new byte[54];

        bis.read(header, 0, 54); //The first 54 bytes are header
        width = bytesToInt(header, 21); //get the width of the image
        height = bytesToInt(header, 25); // get the height of the image

        //if the first bit of the 25th byte is 0, the image is reversed (bottom-up)
        reversed = ((int) header[25] & 0x80) == 0;

        y = new int[height][width];
        cb = new int[height][width];
        cr = new int[height][width];

        if(width * 3 % 4 != 0) {
            emptyBytes = 4 - (width * 3 % 4);
        } else {
            emptyBytes = 0;
        }

        for(int i = 0; i < height; i++) {
            for(int j = 0; j < width; j++) {
                if(i % 2 == 0 && j % 2 == 0) {
                    // for each 2 by 2 block, only read the top left pixel. Other pixels are copied from the top left pixel
                    int high = bis.read();
                    int low = bis.read();

                    y[i][j] = ((high >> 2) & 0x3F) << 2; //decode Y channel
                    cb[i][j] = (((high & 0x03) << 3) | ((low >> 5) & 0x07)) << 3; //decode cb channel
                    cr[i][j] = (low & 0x1F) << 3; //decode cr channel
                } else if(i % 2 == 0 && j % 2 != 0) {
                    y[i][j] = y[i][j - 1];
                    cb[i][j] = cb[i][j - 1];
                    cr[i][j] = cr[i][j - 1];
                } else if(i % 2 != 0 && j % 2 == 0) {
                    y[i][j] = y[i - 1][j];
                    cb[i][j] = cb[i - 1][j];
                    cr[i][j] = cr[i - 1][j];
                } else {
                    y[i][j] = y[i - 1][j - 1];
                    cb[i][j] = cb[i - 1][j - 1];
                    cr[i][j] = cr[i - 1][j - 1];
                }
            }
        }

        fis.close();
        bis.close();
    }

    private RGB yCbCrToRGB(int y, int cb, int cr) { //ycbcr -> rgb
        int r = (int)(y + 1.402 * (cr - 128));
        int g = (int)(y - 0.34414 * (cb - 128) - 0.71414 * (cr - 128));
        int b = (int)(y + 1.772 * (cb - 128));

        r = Integer.min(r, 255);
        g = Integer.min(g, 255);
        b = Integer.min(b, 255);

        r = Integer.max(r, 0);
        g = Integer.max(g, 0);
        b = Integer.max(b, 0);

        return new RGB(r, g, b);
    }

    public void decompress(File file) throws IOException {
        FileOutputStream fos = new FileOutputStream(file);
        BufferedOutputStream bos = new BufferedOutputStream(fos);

        bos.write(header);
        byte[] skip = null;
        if(emptyBytes > 0) {
            skip = new byte[emptyBytes]; //the bytes that are meaningless and at the end of a row
        }

        if(reversed) {
            for(int i = height - 1; i >= 0; i--) {
                for(int j = 0; j < width; j++) {
                    RGB rgb = yCbCrToRGB(y[i][j], cb[i][j], cr[i][j]);
                    bos.write(rgb.b);
                    bos.write(rgb.g);
                    bos.write(rgb.r);
                }

                if(skip != null) {
                    bos.write(skip); //skip the meaningless bytes at the end of a row
                }
            }
        } else {
            for(int i = 0; i < height; i++) {
                for(int j = 0; j < width; j++) {
                    RGB rgb = yCbCrToRGB(y[i][j], cb[i][j], cr[i][j]);
                    bos.write(rgb.b);
                    bos.write(rgb.g);
                    bos.write(rgb.r);
                }

                if(skip != null) {
                    bos.write(skip); //skip the meaningless bytes at the end of a row
                }
            }
        }

        bos.flush();

        fos.close();
        bos.close();
    }
}

class YCbCr {
    public int y;
    public int cb;
    public int cr;

    public YCbCr(int y, int cb, int cr) {
        this.y = y;
        this.cb = cb;
        this.cr = cr;
    }
}

class RGB {
    public int r;
    public int g;
    public int b;

    public RGB(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }
}