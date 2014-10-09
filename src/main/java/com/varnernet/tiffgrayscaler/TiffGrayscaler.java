package com.varnernet.tiffgrayscaler;

import com.sun.media.imageio.plugins.tiff.BaselineTIFFTagSet;
import com.sun.media.imageio.plugins.tiff.TIFFDirectory;
import com.sun.media.imageio.plugins.tiff.TIFFField;
import com.sun.media.imageio.plugins.tiff.TIFFTag;
import com.sun.media.imageio.plugins.tiff.TIFFTagSet;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.text.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.metadata.*;
import javax.imageio.stream.*;
import org.apache.commons.io.IOUtils;

/**
 * Converts TIFF files to indexed grayscale colorspaces.
 */
public class TiffGrayscaler implements Runnable {
    public static void main(String[] args) {
        if (args.length < 3) {
            System.out.println("Usage: tiffgrayscaler <levels> <infile> <outfile>");
            return;
        }
        
        TiffGrayscaler indexer = new TiffGrayscaler(Integer.parseInt(args[0]), args[1], args[2]);
        indexer.run();
    }
    
    private IndexColorModel colorModel;
    private String infile;
    private String outfile;
    
    public TiffGrayscaler(int grays, String infile, String outfile) {
        byte[] levels = new byte[grays];
        for (int i = 0; i < levels.length; i++) {
            levels[i] = (byte)(i * levels.length);
        }
        this.colorModel = new IndexColorModel(4, levels.length, levels, levels, levels);
        
        this.infile = infile;
        this.outfile = outfile;
    }
    
    public void run() {
        ImageIO.scanForPlugins();
        
        FileInputStream inputData = null;
        FileOutputStream normalizedData = null;
        try {
            // Convert the incoming image to the format we desire.
            inputData = new FileInputStream(infile);
            normalizedData = new FileOutputStream(outfile);
            ImageInputStream instream = new MemoryCacheImageInputStream(inputData);
            ImageOutputStream outstream = new MemoryCacheImageOutputStream(normalizedData);
                
            ImageReader reader = null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(instream);
            
            ImageWriter writer = null;
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("tiff");
            
            if (readers != null && writers != null) {
                reader = readers.next();
                writer = writers.next();
                
                // Setup the input stream
                reader.setInput(instream);
                
                // Setup the output stream
                writer.setOutput(outstream);

                // Indexed, 16 levels of gray, deflate compression.
                ImageWriteParam writeParam = writer.getDefaultWriteParam();
                writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                writeParam.setCompressionType("Deflate");
                writeParam.setDestinationType(ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_BYTE_INDEXED));
                
                // For each image (page) in the source...
                int numImages = reader.getNumImages(true);
                writer.prepareWriteSequence(null);
                System.out.println("Number of images: " + numImages);
                
                for (int index = 0; index < numImages; index++) {
                    System.out.println("Index: " + index);
                    
                    // Read the image, calculate a scaled width @ 150DPI
                    Image page = reader.read(index);
                    IIOMetadata meta = reader.getImageMetadata(index);
                    
                    // Default to assume everything is 150 dpi
                    int resUnit = BaselineTIFFTagSet.RESOLUTION_UNIT_INCH;
                    
                    long[] xRes = new long[]{150, 1};
                    long[] yRes = new long[]{150, 1};

                    // Timestamp creation / preservation
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
                    String timestamp = sdf.format(new Date());
                    
                    // Read real resolution settings off the TIFF.
                    try {
                        TIFFDirectory pageDir = TIFFDirectory.createFromMetadata(meta);
                        TIFFField xResTag = pageDir.getTIFFField(BaselineTIFFTagSet.TAG_X_RESOLUTION);
                        TIFFField yResTag = pageDir.getTIFFField(BaselineTIFFTagSet.TAG_Y_RESOLUTION);
                        TIFFField resUnitTag = pageDir.getTIFFField(BaselineTIFFTagSet.TAG_RESOLUTION_UNIT);
                        if (resUnitTag != null) {
                            resUnit = Integer.parseInt(resUnitTag.getValueAsString(0));
                        }
                        
                        TIFFField dtField = pageDir.getTIFFField(BaselineTIFFTagSet.TAG_DATE_TIME);
                        if (dtField != null) {
                            timestamp = dtField.getAsString(0);
                        }
                        
                        if (xResTag != null && yResTag != null) {
                            xRes = xResTag.getAsRational(0);
                            yRes = yResTag.getAsRational(0);
                            
                            // Convert CM to Inches.
                            if (resUnit == BaselineTIFFTagSet.RESOLUTION_UNIT_CENTIMETER) {
                                xRes[0] = Math.round(xRes[0] * (2.571428571 / xRes[1]));
                                xRes[1] = 1;
                                yRes[0] = Math.round(yRes[0] * (2.571428571 / yRes[1]));
                                yRes[1] = 1;
                                resUnit = BaselineTIFFTagSet.RESOLUTION_UNIT_INCH;
                            } else if (resUnit == BaselineTIFFTagSet.RESOLUTION_UNIT_INCH && 
                                      (xRes[1] > 1 || yRes[1] > 1)) 
                            {
                                // Normalize to one-inch measurements.
                                xRes[0] = Math.round(xRes[0] / xRes[1]);
                                xRes[1] = 1;
                                yRes[0] = Math.round(yRes[0] / yRes[1]);
                                yRes[1] = 1;
                            }
                        }
                    } catch (IIOInvalidTreeException notAtiff) {
                        // TODO: Get the width and xresolution using just IIOMetadata.
                    }
                    
                    // If it's not the correct resolution, rescale it.
                    if (xRes[0] != 150) {
                        double originalXRes = xRes[0];
                        
                        // Scale based on horizontal resolution. Passing -1 to height means "preserve aspect ratio".
                        page = page.getScaledInstance((int)((150 * page.getWidth(null)) / xRes[0]), -1, Image.SCALE_FAST);
                        
                        // We now are scaled to 150DPI, calculate the y-DPI using the multiplier applied to the x-resolution.
                        // This will scale the yResolution proporitionately, preserving the aspect ratio where x and y resolutions
                        // differ.
                        xRes[0] = 150;
                        yRes[0] = (long)(yRes[0] * (xRes[0] / originalXRes));
                    }
                    BufferedImage rescaled = new BufferedImage(page.getWidth(null), page.getHeight(null), BufferedImage.TYPE_BYTE_INDEXED, colorModel);
                    Graphics2D g = rescaled.createGraphics();
                    g.drawImage(page, 0, 0, null);
                    g.dispose();

                    // Set the xResolution, yResolution, resolutionUnit, and DateTime.
                    TIFFDirectory rescaledPageDir = new TIFFDirectory(new TIFFTagSet[] {BaselineTIFFTagSet.getInstance()}, null);
                    
                    rescaledPageDir.addTIFFField(new TIFFField(
                            BaselineTIFFTagSet.getInstance().getTag(BaselineTIFFTagSet.TAG_DATE_TIME),
                            TIFFTag.TIFF_ASCII, 1, new String[] {timestamp}));
                    rescaledPageDir.addTIFFField(new TIFFField(
                            BaselineTIFFTagSet.getInstance().getTag(BaselineTIFFTagSet.TAG_RESOLUTION_UNIT),
                            TIFFTag.TIFF_SHORT, 1, new char[] {BaselineTIFFTagSet.RESOLUTION_UNIT_INCH}));
                    rescaledPageDir.addTIFFField(new TIFFField(
                            BaselineTIFFTagSet.getInstance().getTag(BaselineTIFFTagSet.TAG_X_RESOLUTION),
                            TIFFTag.TIFF_RATIONAL, 1, new long[][]{xRes}));
                    rescaledPageDir.addTIFFField(new TIFFField(
                            BaselineTIFFTagSet.getInstance().getTag(BaselineTIFFTagSet.TAG_Y_RESOLUTION),
                            TIFFTag.TIFF_RATIONAL, 1, new long[][]{yRes}));
                    
                    // Convert back to IIOMetadata...
                    IIOMetadata rescaledMeta = rescaledPageDir.getAsMetadata();
                    
                    writer.writeToSequence(new IIOImage(rescaled, null, rescaledMeta), writeParam);
                }
                writer.endWriteSequence();
                outstream.flush();
                outstream.close();
                reader.dispose();
                writer.dispose();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            IOUtils.closeQuietly(inputData);
            IOUtils.closeQuietly(normalizedData);
        }
    }
}
